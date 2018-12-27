package com.lowang.proxy.hls;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import cn.hutool.cache.Cache;
import cn.hutool.cache.CacheUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
/**
 * 代理服务
 *
 * @author wang.ch
 * @date 2018-12-25 17:29:30
 */
@Controller("proxyController")
public class ProxyController {
  private static final Logger LOG = LoggerFactory.getLogger(ProxyController.class);
  private static final String AES_KEY_URL = "http://sjlivecdnx.cbg.cn/1ive/stream_3.php";
  private static final String M3U8_URL =
      "http://sjlivecdn.cbg.cn/201812251521/3b3beea03376986e13844c819e25816c/app_2/_definst_/ls_3.stream/chunklist.m3u8";
  private static final String TS_PREFIX =
      "http://sjlivecdn.cbg.cn/201812251521/3b3beea03376986e13844c819e25816c/app_2/_definst_/ls_3.stream/";
  private static final String M3U8_MD5_URL =
      "http://app.cbg.cn/?app=activity&controller=wwsp&action=hlive_md5&callback=jQuery&ch=%2Fapp_2%2F_definst_%2Fls_3.stream%2Fchunklist.m3u8&_=";
  private static final Cache<String, byte[]> LFU_CACHE = CacheUtil.newFIFOCache(30);
  private static byte[] keyData = null;
  @Autowired private Environment env;
  private String prefix;
  private long m3u8Timestamp = -1;
  private String m3u8Url = null;

  private long timeout = 1000 * 60 * 1;

  public static byte[] getKeyData() {
    if (keyData == null) {
      synchronized (ProxyController.class) {
        byte[] bs =
            HttpUtil.createGet(AES_KEY_URL)
                .header("Referer", "http://www.cbg.cn/zbpd/")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3610.2 Safari/537.36")
                .header("X-Requested-With", "ShockwaveFlash/32.0.0.101")
                .execute()
                .bodyBytes();
        keyData = bs;
        return bs;
      }
    } else {
      return keyData;
    }
  }

  public void cacheTsData(String content) {
    Pattern p = Pattern.compile("media(.*)ts");
    Matcher m = p.matcher(content);
    while (m.find()) {
      String file = m.group();
      if (!LFU_CACHE.containsKey(file)) {
        LOG.info("found a new media file:{}", file);
        final String tsDataUrl =
            (prefix != null ? prefix : env.getProperty("cqtv3.ts-prefix", TS_PREFIX)) + file;
        byte[] data = getTsData(tsDataUrl);
        LFU_CACHE.put(file, data);

        LOG.info(
            "cache media file:{},file size:{}MB,cache size:{}",
            file,
            BigDecimal.valueOf(data.length / 1024 / 1024.00)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString(),
            LFU_CACHE.size());
      }
    }
  }

  @ResponseBody
  @RequestMapping(value = "/ocqtv3.m3u8", produces = "application/vnd.apple.mpegurl")
  public String ocqtv3(HttpServletRequest request, HttpServletResponse response) {
    String content = getM3u8Data();
    content = content.replaceAll("media", prefix.trim() + "media");
    // content = content.replaceFirst("#EXT-X-KEY(.*)php\"\n", "");
    // LOG.info("output :{}", content);
    return content;
  }

  @ResponseBody
  @RequestMapping(value = "/cqtv3.m3u8", produces = "application/vnd.apple.mpegurl")
  public String cqtv3(HttpServletRequest request, HttpServletResponse response) {
    String content = getM3u8Data();
    cacheTsData(content);
    content = content.replaceAll("media", "/cqtv3/media");
    content = content.replaceFirst("#EXT-X-KEY(.*)php\"\n", "");
    // LOG.info("output :{}", content);
    return content;
  }

  @RequestMapping(value = "/cqtv3/{file}", produces = "video/MP2T")
  @ResponseBody
  public byte[] cqtv3hls(@PathVariable(name = "file") final String file) {
    LOG.info("request file:{}", file);
    String ivStr = file.substring(file.indexOf("_") + 1, file.indexOf(".ts"));
    LOG.info("request file:{}", ivStr);
    byte[] iv = new byte[16];
    char[] cs = ivStr.toCharArray();
    for (int i = 0; i < cs.length; i++) {
      iv[15 - i] = (byte) Integer.valueOf(cs[i] + "").intValue();
    }
    for (int i = 0; i < 16 - cs.length; i++) {
      iv[i] = 0;
    }
    LOG.info("media iv:{}", new String(HexUtil.encodeHex(iv)));
    byte[] key = getKeyData();
    LOG.info("media key:{}", new String(HexUtil.encodeHex(key)));
    AES aes = new AES("CBC", "PKCS7Padding", key, iv);
    byte[] data = LFU_CACHE.get(file);
    if (data == null) {
      final String tsDataUrl =
          (prefix != null ? prefix : env.getProperty("cqtv3.ts-prefix", TS_PREFIX)) + file;
      data = getTsData(tsDataUrl);
      LFU_CACHE.put(file, data);
    } else {
      LOG.info("media from cache:{}", file);
    }
    if (data == null) return null;
    try {
      data = aes.decrypt(data);
    } catch (Exception e) {
      LOG.error("decrypte error:{}", e);
      return null;
    }
    LOG.info("decrypt success:{}", file);
    return data;
  }

  public String getM3u8Data() {
    final String m3u8Url = getMd5M3u8();
    prefix = m3u8Url.substring(0, m3u8Url.lastIndexOf("/") + 1);
    final String content =
        HttpUtil.createGet(m3u8Url).header("Referer", "http://www.cbg.cn").execute().body();
    return content;
  }

  public String getMd5M3u8() {
    if (m3u8Timestamp == -1 || System.currentTimeMillis() - m3u8Timestamp > timeout) {
      String url = M3U8_MD5_URL + System.currentTimeMillis();
      HttpResponse resp =
          HttpUtil.createGet(url)
              .header("Accept", "*/*")
              .header("Accept-Encoding", "gzip, deflate")
              .header("Accept-Language", "zh-CN,zh;q=0.9")
              .header("Host", "sjlivecdn.cbg.cn")
              .header("Referer", "http://www.cbg.cn/zbpd/")
              .header(
                  "User-Agent",
                  "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3610.2 Safari/537.36")
              .header("X-Requested-With", "ShockwaveFlash/32.0.0.101")
              .execute();
      if (resp.getStatus() != 200) {
        return env.getProperty("cqtv3.m3u8", M3U8_URL);
      }
      String content = resp.body();
      LOG.info("orignal m3u8 :{}", content);
      content = content.replaceAll(".*jQuery\\(\"", "").replace("\")", "").replaceAll("\\\\", "");
      LOG.info("get orignal m3u8 url:{}", content);
      m3u8Timestamp = System.currentTimeMillis();
      m3u8Url = content;
      return content;
    } else {
      return m3u8Url != null ? m3u8Url : env.getProperty("cqtv3.m3u8", M3U8_URL);
    }
  }

  public byte[] getTsData(String url) {
    LOG.info("request for url:{}", url);
    HttpResponse resp =
        HttpUtil.createGet(url)
            .header("Accept", "*/*")
            .header("Accept-Encoding", "gzip, deflate")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .header("Host", "sjlivecdn.cbg.cn")
            .header("Referer", "http://www.cbg.cn/zbpd/")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3610.2 Safari/537.36")
            .header("X-Requested-With", "ShockwaveFlash/32.0.0.101")
            .execute();
    if ("text/html".equalsIgnoreCase(resp.header("Content-Type")) || resp.getStatus() != 200) {
      LOG.warn("get ts data :{},return other status:{}", url, resp.getStatus());
      return null;
    } else {
      return resp.bodyBytes();
    }
  }
}

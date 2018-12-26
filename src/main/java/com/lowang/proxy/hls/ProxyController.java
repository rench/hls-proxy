package com.lowang.proxy.hls;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

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
  private static final Cache<String, byte[]> LFU_CACHE = CacheUtil.newLFUCache(10);
  private static byte[] keyData = null;
  @Autowired private Environment env;

  @ResponseBody
  @RequestMapping("/cqtv3.m3u8")
  public String cqtv3(HttpServletRequest request) {
    String content = getM3u8Data("");
    cacheTsData(content);
    content = content.replaceAll("media", "/cqtv3/media");
    return content;
  }

  public void cacheTsData(String content) {
    Pattern p = Pattern.compile("media(.*)ts");
    Matcher m = p.matcher(content);
    while (m.find()) {
      String file = m.group();
      if (!LFU_CACHE.containsKey(file)) {
        final String tsDataUrl = env.getProperty("cqtv3.ts-prefix", TS_PREFIX) + file;
        byte[] data = getTsData(tsDataUrl);
        LFU_CACHE.put(file, data);
        LOG.info("cache media file:{}", file);
      }
    }
    LOG.info("cache size: {}", LFU_CACHE.size());
  }

  @RequestMapping("/cqtv3/{file}")
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
      final String tsDataUrl = env.getProperty("cqtv3.ts-prefix", TS_PREFIX) + file;
      data = getTsData(tsDataUrl);
      LFU_CACHE.put(file, data);
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

  public String getM3u8Data(String url) {
    final String m3u8Url = env.getProperty("cqtv3.m3u8", M3U8_URL);
    final String content =
        HttpUtil.createGet(m3u8Url).header("Referer", "http://www.cbg.cn").execute().body();
    return content;
  }

  public byte[] getTsData(String url) {
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
}

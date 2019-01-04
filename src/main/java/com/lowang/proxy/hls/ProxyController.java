package com.lowang.proxy.hls;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import cn.hutool.cache.Cache;
import cn.hutool.cache.CacheUtil;
import cn.hutool.core.collection.BoundedPriorityQueue;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
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
  private static final String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3610.2 Safari/537.36";
  private static final String AES_KEY_URL = "http://sjlivecdnx.cbg.cn/1ive/stream_3.php";
  private static final String M3U8_MD5_URL =
      "http://app.cbg.cn/?app=activity&controller=wwsp&action=hlive_md5&callback=jQuery&ch=%2Fapp_2%2F_definst_%2Fls_3.stream%2Fchunklist.m3u8&_=";
  private static final Cache<String, byte[]> LFU_CACHE = CacheUtil.newFIFOCache(30);
  private static final Cache<String, Long> LFU_TS_CACHE = CacheUtil.newFIFOCache(30);
  private static final BoundedPriorityQueue<String> QUEUE =
      new BoundedPriorityQueue<>(
          30,
          new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
              return -o1.compareTo(o2);
            }
          });
  private static byte[] keyData = null;
  private String prefix;
  private long m3u8Timestamp = -1;
  private String m3u8Url = null;
  private long refetchM3u8Timeout = 1000 * 60 * 10;
  private static final Map<String, String> LIVE_HEADER;
  private String m3u8Data = "";
  private long m3u8LastAccess = System.currentTimeMillis();
  private long tsLastAccess = System.currentTimeMillis();
  private long cacheTsTimeout = 1000 * 60 * 1;
  private long cacheM3u8Timeout = 1000 * 60 * 2;
  @Autowired private Environment env;

  static {
    LIVE_HEADER = new HashMap<>();
    LIVE_HEADER.put("User-Agent", USER_AGENT);
    LIVE_HEADER.put("Referer", "http://www.cbg.cn/1ive/");
    LIVE_HEADER.put("X-Requested-With", "ShockwaveFlash/32.0.0.101");
  }

  public static byte[] getKeyData() {

    if (keyData == null) {
      synchronized (ProxyController.class) {
        byte[] bs = null;
        HttpResponse resp = HttpUtil.createGet(AES_KEY_URL).addHeaders(LIVE_HEADER).execute();
        if (resp.getStatus() != 200) {
          LOG.warn("try to get stream key error -> {}", resp.body());
          return new byte[] {};
        }
        bs = resp.bodyBytes();
        keyData = bs;
        return bs;
      }
    } else {
      return keyData;
    }
  }

  public List<String> extractTsFile(String m3u8Content) {
    List<String> list = new ArrayList<>();
    Pattern p = Pattern.compile("media(.*)ts");
    Matcher m = p.matcher(m3u8Content);
    while (m.find()) {
      String file = m.group();
      list.add(file);
    }
    return list;
  }

  public void cacheTsData(String content) {
    Pattern p = Pattern.compile("media(.*)ts");
    Matcher m = p.matcher(content);
    while (m.find()) {
      String file = m.group();
      if (!LFU_CACHE.containsKey(file)) {
        LOG.info("found a new media file:{}", file);
        final String tsDataUrl =
            (prefix != null ? prefix : env.getProperty("cqtv3.ts-prefix", "")) + file;
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
  @RequestMapping(value = "/stream_3.php", produces = "binary/octet-stream")
  public byte[] stream(HttpServletRequest request, HttpServletResponse response) {
    Enumeration<String> headerNames = request.getHeaderNames();
    while (headerNames.hasMoreElements()) {
      String key = (String) headerNames.nextElement();
      String value = request.getHeader(key);
      LOG.info("stream_3.php:{}->{}", key, value);
    }
    LOG.info("stream_3.php:ip->{}", request.getRemoteAddr());
    return getKeyData();
  }

  @ResponseBody
  @RequestMapping(value = "/ocqtv3.m3u8", produces = "application/vnd.apple.mpegurl")
  public String ocqtv3(HttpServletRequest request, HttpServletResponse response) {
    m3u8LastAccess = System.currentTimeMillis();
    String content = getM3u8Data();
    content = content.replaceAll("media", prefix.trim() + "media");
    content = content.replace("http://sjlivecdnx.cbg.cn/1ive/stream_3.php", "stream_3.php");
    List<String> list = extractTsFile(content);
    list = list.stream().filter((x) -> !LFU_TS_CACHE.containsKey(x)).collect(Collectors.toList());
    if (!list.isEmpty()) {
      QUEUE.addAll(list);
      list.forEach(x -> LFU_TS_CACHE.put(x, System.currentTimeMillis()));
    }
    // content = content.replaceAll("media", "/cqtv3/media");
    // content = content.replaceFirst("#EXT-X-KEY(.*)php\"\n", "");
    // content = content.replaceFirst("#EXT-X-ALLOW-CACHE:NO\n", "");
    // content = content.replaceFirst("EXT-X-TARGETDURATION:12", "EXT-X-TARGETDURATION:10");
    list = new ArrayList<>();
    for (int i = 0; i < 3; i++) {}
    // LOG.info("output :{}", content);
    return content;
  }

  @ResponseBody
  @RequestMapping(value = "/cqtv3.m3u8", produces = "application/vnd.apple.mpegurl")
  public String cqtv3(HttpServletRequest request, HttpServletResponse response) {
    m3u8LastAccess = System.currentTimeMillis();
    String content = getM3u8Data();
    cacheTsData(content);
    content = content.replaceAll("media", "/cqtv3/media");
    content = content.replaceFirst("#EXT-X-KEY(.*)php\"\n", "");
    content = content.replaceFirst("#EXT-X-ALLOW-CACHE:NO\n", "");
    // LOG.info("output :{}", content);
    return content;
  }

  @RequestMapping(value = "/cqtv3/{file}")
  @ResponseBody
  public byte[] cqtv3hls(
      @PathVariable(name = "file") final String file,
      HttpServletRequest request,
      HttpServletResponse response) {
    tsLastAccess = System.currentTimeMillis();
    LOG.info("request file:{}", file);
    String ivStr = file.substring(file.indexOf("_") + 1, file.indexOf(".ts"));
    ivStr = String.format("%032x", Integer.valueOf(ivStr));
    ivStr = StrUtil.padPre(ivStr, 32, '0');
    byte[] iv = HexUtil.decodeHex(ivStr);
    LOG.info("media iv:{}", ivStr);
    byte[] key = getKeyData();
    LOG.info("media key:{}", new String(HexUtil.encodeHex(key)));
    AES aes = new AES("CBC", "PKCS7Padding", key, iv);
    byte[] data = LFU_CACHE.get(file);
    if (data == null) {
      final String tsDataUrl =
          (prefix != null ? prefix : env.getProperty("cqtv3.ts-prefix", "")) + file;
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
    }
    LOG.info("decrypt success:{}", file);
    response.setHeader("Accept-Ranges", "bytes");
    response.setContentLength(data.length);
    response.setHeader("Connection", "keep-alive");
    response.setContentType(" video/mpeg");
    response.setHeader("Server", "FlashCom/3.5.7");
    response.setHeader("Cache-Control", "max-age=360000");
    return data;
  }

  public String getM3u8Data() {
    if (StrUtil.isBlank(m3u8Data)) {
      LOG.info("get m3u8 data miss cache");
      final String m3u8Url = getMd5M3u8();
      prefix = m3u8Url.substring(0, m3u8Url.lastIndexOf("/") + 1);
      final String content =
          HttpUtil.createGet(m3u8Url).addHeaders(LIVE_HEADER).disableCache().execute().body();
      m3u8Data = content;
      return content;
    } else {
      return m3u8Data;
    }
  }

  public void getM3u8DataAuto() {
    final String m3u8Url = getMd5M3u8();
    prefix = m3u8Url.substring(0, m3u8Url.lastIndexOf("/") + 1);
    HttpResponse resp =
        HttpUtil.createGet(m3u8Url).addHeaders(LIVE_HEADER).disableCache().execute();
    if (resp.getStatus() == 200) {
      String content = resp.body();
      m3u8Data = content;
      LOG.info("getM3u8DataAuto success");
    } else {
      LOG.error("getM3u8DataAuto error -> {}", resp);
    }
  }

  public String getMd5M3u8() {
    if (m3u8Timestamp == -1 || System.currentTimeMillis() - m3u8Timestamp > refetchM3u8Timeout) {
      String url = M3U8_MD5_URL + System.currentTimeMillis();
      HttpResponse resp = HttpUtil.createGet(url).addHeaders(LIVE_HEADER).disableCache().execute();
      if (resp.getStatus() != 200) {
        LOG.warn("try to get m3u8 url failed -> {}", resp.body());
        return env.getProperty("cqtv3.m3u8", "");
      }
      String content = resp.body();
      LOG.info("orignal m3u8 :{}", content);
      content = content.replaceAll(".*jQuery\\(\"", "").replace("\")", "").replaceAll("\\\\", "");
      LOG.info("get orignal m3u8 url:{}", content);
      m3u8Timestamp = System.currentTimeMillis();
      m3u8Url = content;
      return content;
    } else {
      return m3u8Url != null ? m3u8Url : env.getProperty("cqtv3.m3u8", "");
    }
  }

  @Scheduled(fixedRate = 5000)
  public void getMd5M3u8Auto() {
    if (System.currentTimeMillis() - m3u8LastAccess < cacheM3u8Timeout) {
      getM3u8DataAuto();
    }
    if (System.currentTimeMillis() - tsLastAccess < cacheTsTimeout) {
      cacheTsData(m3u8Data);
    }
  }

  public byte[] getTsData(String url) {
    LOG.info("request for url:{}", url);
    HttpResponse resp = HttpUtil.createGet(url).addHeaders(LIVE_HEADER).execute();
    if ("text/html".equalsIgnoreCase(resp.header("Content-Type")) || resp.getStatus() != 200) {
      LOG.warn("get ts data :{},return other status:{}", url, resp.getStatus());
      return null;
    } else {
      return resp.bodyBytes();
    }
  }

  public static void main(String[] args) {
    BoundedPriorityQueue<String> q =
        new BoundedPriorityQueue<>(
            2,
            new Comparator<String>() {
              @Override
              public int compare(String o1, String o2) {
                return -o1.compareTo(o2);
              }
            });
    q.add("media-1.ts");
    q.add("media-1.ts");
    q.add("media-2.ts");
    // q.add("media-3.ts");
    System.out.println(q.poll());
  }
}

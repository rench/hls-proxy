package com.lowang.proxy.hls;

import java.io.InputStream;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.http.HttpUtil;

public class HlsProxyTest {
  public static void main(String[] args) {
    final String aesDataUrl = "http://sjlivecdnx.cbg.cn/1ive/stream_3.php";
    byte[] bs =
        HttpUtil.createGet(aesDataUrl)
            .header("Referer", "http://www.cbg.cn/zbpd/")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3610.2 Safari/537.36")
            .header("X-Requested-With", "ShockwaveFlash/32.0.0.101")
            .execute()
            .bodyBytes();
    System.out.println(HexUtil.encodeHex(bs));
    byte[] iv = new byte[16];
    iv[15] = 2;
    iv[14] = 3;
    iv[13] = 5;
    iv[12] = 1;
    iv[11] = 5;
    iv[10] = 8;
    for (int i = 0; i < 10; i++) {
      iv[i] = 0;
    }
    System.out.println(HexUtil.encodeHex(iv));
    AES aes = new AES("CBC", "PKCS7Padding", bs, iv);
    byte[] data = FileUtil.readBytes("D://media-uhyiqgc82_261237.ts");
    data = aes.decrypt(data);
    FileUtil.writeBytes(data, "D://media-uhyiqgc82_261237_decode.ts");
  }

  public static void main1(String[] args) {
    getTs("http://sjlivecdn.cbg.cn/201812260830/36c905f96837dfc439679a1f84ddcccc/app_2/_definst_/ls_3.stream/media-uhyiqgc82_241437.ts");
  }
  
  public static void getTs(String url) {
    System.out.println(HttpUtil.createGet(url).header("Referer", "http://www.cbg.cn").execute().getStatus());
  }
  
  public static InputStream getTsData(String url) {
    return HttpUtil.createGet(url).header("Referer", "http://www.cbg.cn").execute().bodyStream();
  }
}

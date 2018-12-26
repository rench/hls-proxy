package com.lowang.proxy.hls;

import java.io.InputStream;

import cn.hutool.http.HttpUtil;

public class M3u8Test {

  public static void main(String[] args) {
    final String m3u8Url =
        "http://sjlivecdn.cbg.cn/201812251521/3b3beea03376986e13844c819e25816c/app_2/_definst_/ls_3.stream/chunklist.m3u8";
    final String content =
        HttpUtil.createGet(m3u8Url).header("Referer", "http://www.cbg.cn").execute().body();
    System.out.println(content);
  }

  public static InputStream getTsData(String url) {
    return HttpUtil.createGet(url).header("Referer", "http://www.cbg.cn").execute().bodyStream();
  }
}

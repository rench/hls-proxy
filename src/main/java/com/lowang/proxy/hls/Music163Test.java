package com.lowang.proxy.hls;

import cn.hutool.core.codec.Base64;
import cn.hutool.crypto.symmetric.AES;

public class Music163Test {

  public static void main(String[] args) {
    String key = "#14ljk_!\\]&0U<'(";
    AES aes = new AES("ECB", "PKCS7Padding", key.getBytes());

    String base64 =
        "L64FU3W4YxX3ZFTmbZ+8/dKT9MoEDtuuXtSJM66mXC55J/KN7BDVP+3D5hs+nd2OfYCs2czuCDwaLLudCqC/EuLrPcTSS+Q4/yHiJtvF/ynqWnpZbn7zMubHIKu9msme1/c/0FSWvwHnQJirViOTKxg136s014agaLb9aILz/o7u3U6kXhLq478C+AJD1PmTn8fzyUo0yLNpzpWy3pIGoR3h9btuBfWM6/xm99Gv1Z1+/qbT8temhqqkngka4tbn0hrwAoSWNsH8tl8Hg4VVhC63U5fWWaBu43ypEttGKcp9cQ+eGZcn9kvns5nUFNdD5KcLuFDYsvScvI6oeIKGF3sozK71VYWB8Ve1HKhpuOdugGh1Jg7HTYmiZ/Mdr6JMATaXWx6iV+4XNgJn6HJI5jmEk0HnnGBdDmqjtRRl1GTvRAgZhoD6H5cgXzX0Mpir8eXhtBpQOpOE4dRDVJsqYZHAwqeUG9ibZw39HAjBPdA=";

    byte[] data = Base64.decode(base64);

    data = aes.decrypt(data);
    System.out.println(new String(data));
  }
}

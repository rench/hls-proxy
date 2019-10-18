package com.lowang.proxy.hls;

import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.PublicKey;

import cn.hutool.crypto.asymmetric.KeyType;
import cn.hutool.crypto.asymmetric.RSA;

public class RSAUtils {

  public static void main(String[] args) {

    RSA rsa = new RSA();

    String data = "你好";

    byte[] encData = rsa.encrypt(data, KeyType.PublicKey);

    byte[] decData = rsa.decrypt(encData, KeyType.PrivateKey);
    System.out.println(new String(decData));
  }
}

package com.lowang.proxy.hls;

import java.security.Security;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.HexUtil;

/**
 * AES 加解密工具
 *
 * @author Wang.ch
 */
public class AESUtil {

  public static boolean initialized = false;

  public static final String ALGORITHM = "AES/CBC/PKCS7Padding";

  /**
   * 加密字符串
   *
   * @param str 要被加密的字符串
   * @param key 加/解密要用的长度为32的字节数组（256位）密钥
   * @return byte[] 加密后的字节数组
   */
  public static byte[] Aes256Encode(String str, byte[] key) {
    initialize();
    byte[] result = null;
    try {
      Cipher cipher = Cipher.getInstance(ALGORITHM, "BC");
      SecretKeySpec keySpec = new SecretKeySpec(key, "AES"); // 生成加密解密需要的Key
      cipher.init(Cipher.ENCRYPT_MODE, keySpec);
      result = cipher.doFinal(str.getBytes("UTF-8"));
    } catch (Exception e) {
      e.printStackTrace();
    }
    return result;
  }

  /**
   * 解密字符串
   *
   * @param bytes 要被解密的字节数组
   * @param key 加/解密要用的长度为32的字节数组（256位）密钥
   * @return String 解密后的字符串
   */
  public static String Aes256Decode(byte[] bytes, byte[] key) {
    initialize();
    String result = null;
    try {
      Cipher cipher = Cipher.getInstance(ALGORITHM, "BC");
      SecretKeySpec keySpec = new SecretKeySpec(key, "AES"); // 生成加密解密需要的Key
      cipher.init(Cipher.DECRYPT_MODE, keySpec);
      byte[] decoded = cipher.doFinal(bytes);
      result = new String(decoded, "UTF-8");
    } catch (Exception e) {
      e.printStackTrace();
    }
    return result;
  }

  /**
   * 解密字符串
   *
   * @param bytes 要被解密的字节数组
   * @param key 加/解密要用的长度为32的字节数组（256位）密钥
   * @return String 解密后的字符串
   */
  public static byte[] Aes256Decode(byte[] bytes, byte[] key, byte[] iv) {
    initialize();
    byte[] result = null;
    try {
      Cipher cipher = Cipher.getInstance(ALGORITHM, "BC");
      SecretKeySpec keySpec = new SecretKeySpec(key, "AES"); // 生成加密解密需要的Key
      IvParameterSpec ivSpec = new IvParameterSpec(iv);
      cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
      byte[] decoded = cipher.doFinal(bytes);
      return decoded;
      // result = new String(decoded, "UTF-8");
    } catch (Exception e) {
      e.printStackTrace();
    }
    return result;
  }

  /** 初始化 */
  public static void initialize() {
    if (initialized) return;
    Security.addProvider(new BouncyCastleProvider());
    initialized = true;
  }

  public static void main(String[] args) {
    
    System.out.println( String.format("%032x", 261237) );
    
    byte[] iv = new byte[16];
    iv[15] = 1;
    byte[] out =
        Aes256Decode(
            FileUtil.readBytes("D://media-uhyiqgc82_261237.ts"),
            HexUtil.decodeHex("e1e9e107437103e4b7f8eadb5f95e928"),
            // HexUtil.decodeHex("00000000000000000000000000261237"));
            HexUtil.decodeHex("0000000000000000000000000003fc75"));
    FileUtil.writeBytes(out, "D://media-uhyiqgc82_261237_java_cbc_3.ts");
  }
}

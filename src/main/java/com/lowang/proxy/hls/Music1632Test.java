package com.lowang.proxy.hls;

import cn.hutool.core.codec.Base64;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.json.JSONObject;

public class Music1632Test {

  static String ivParameter = "0102030405060708";
  // RSA
  static String rsaKey =
      "acd03606e1edb992700fd6708c4e41211a287363b03dff6e6fdac2841bc32ca8b36c97a018fbe5877ada81cea92fe550c45022f53d676bea6628dfa2220cdc62017cfbd9f9d6374f2554c47a282216163c7b0775b3a890c37fd096242b04920ecc68f981ab8b3de50dbfbf437968ee55031dadcdc5b881fe4222281005c1011c";
  static String key = "0CoJUm6Qyw8W8jud";
  static String sKey = "9ROymo54HBMpdCxi";

  public static void main(String[] args) {

    System.out.println(sKey.getBytes().length);
    AES aes = new AES("CBC", "PKCS5Padding", sKey.getBytes(), ivParameter.getBytes());
    AES aes2 = new AES("CBC", "PKCS5Padding", key.getBytes(), ivParameter.getBytes());
    String encData =
        "KhS3LZy7eX/5JoM6CbpYyzoJqxEdjOEPtpyH9h9Nv1GNajoeuMNYv5AxIgNhCVp8HRGSA7w4iBTy4uVPWJ03MlkXGEOXc33Xn+Tk/2ak6oX/5u8Lzrc3eLApA3hHyMFw/6l5Z2/kWKZ6vADej3rBck2yzWrbAdEh2WFwMLq0JlzQuWD7fHbwU4n52HWpds8YT3kPjuAlyEKOeHeAeGikCs6itCdsdJpuD8HNy+rEcXOjIxQNiuUVwESfVEKtU7+m/ntcf3PXlm3Z3uhrbvqbqsxqiO29NK2ykoQ+D+sOPlwPVf2xmBMo8Qf++duq7/dj";
    encData = "1Rq0Y5GfQdhwasp4fHIUBIdGON9hpYhUoL8oNHQ8kMZpj6d5IFz/RaDPJMgQxlbdM5Dw2YwhLsekJq6tldSjtASTS84N1T1x7eilN7lvJDLe5P6EXDPmBj+W8XGVCaWtG/elPaYSECVk+v+qN7R5Zw==";
    encData = "lGVvHZ/O/Q0Gh6jRMi1GIPEc+KSKRYGjcenEbOK2KmidHluo6D9r1wJ+T07alm90SURVrFUMZitYS+SgAhQbOMA5N03oiby9jIKmiabgZ689UYAKd2/M1qbWvZie3qxQ4RWFY2bq0irvVfl4o1zc2g==";
    byte[] bs = Base64.decode(encData);

    bs = aes.decrypt(bs);
    System.out.println(new String(bs));
    bs = Base64.decode(bs);
    bs = aes2.decrypt(bs);
    System.out.println(new String(bs));

    get();
  }

  public static void get() {
    String api = "/weapi/song/enhance/player/url/v1";

    String songid = "1373002790";

    JSONObject param = new JSONObject();
    param.put("csrf_token", "");
    param.put("encodeType", "flac");
    param.put("ids", "["+songid+"]");
    param.put("level", "standard");
    String str = param.toString();
    System.out.println(str);

    AES aes = new AES("CBC", "PKCS5Padding", sKey.getBytes(), ivParameter.getBytes());
    AES aes2 = new AES("CBC", "PKCS5Padding", key.getBytes(), ivParameter.getBytes());

    byte[] bs = aes2.encrypt(str.getBytes());

    str = Base64.encode(bs);

    bs = aes.encrypt(str.getBytes());

    str = Base64.encode(bs);
    
    System.out.println(str ); 

    JSONObject obj = new JSONObject();
    obj.put("encSecKey", rsaKey);
    obj.put("encText", str);
  }
}

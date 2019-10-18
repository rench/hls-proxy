package com.lowang.proxy.hls;

import cn.hutool.crypto.symmetric.AES;

public class AesTest {

  public static void main(String[] args) {
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
    AES aes = new AES("ECB", "PKCS7Padding", "dJLdCJiVnDvM9JUp".getBytes());
    aes.decryptFromBase64(
        "znq6Ob4SNy9KAkGnKq34SRVAHT1Yn126aLkgMzrwwMVaE6kz4LBxHJBReaXLjjrFpzm8xZBcfqNqujjmcgCsoEqFiRZYXUX+8K+z+egxV9ISnLAoO4Z1OVaFQ58ZSXxyO58SWTxDe2cq1QC4tAf9uDWE2fOKzzZqX4zqpHGGnk+XEtG8DTzT7EXFIg4a0ZZS6MyWBgRLg2V3SmpNebZmrOc24a1Ga3vbcqOADypokD3JHxPEQPE+0ADcqyrXucKsvCwGMNaOWAfP7NCA7cii8ZxG6UCr+TVY3bSntG2NfsrB11plfHy/Rx23RvBXqywYgXO08ER3XsOrZtafD07aIi6GQmBkt3zEkwVdZ33eV1Yo5URwTGqDsXuWiCj67xGNZNm+kVApfXBcXnW1FxiwZQmMwaeyqiBkuIXt5ZNaLpCT36FGS9MXGhIzfjRuklh9GWQwcez0O2XL5a7ySYbwHjg1xn+Z1ydR6ZMYM5II6dap8EpZSDgx8NziuDN9YkKYQiXXhIfkCBHrKkfErJB6uzaeK2rn8wlGVPFI0VZTnG8Fe+zqtZW7TizvLlufRE+1HBq7K8I+M72v8BZhgO7LTbCwEBlE8HOkXJ5F6v5P4/1DcpPayIAtsHrRXsORxKHApg4r4R08xXyluAA4BcaojxqGbFQ+WumRyCxZK3Wf1xzwRrZ/NOPtbGd+0DiLoR7uETHBS1hNpmAafgP/mLRLeJtU/yfRRS7d98ST+AM61x6NodQdL9K90GoVGcAMtKyVy73Wi50O5dUKjTkU0Kw2bVW2j0B1ZcADPmMg69uf7P1Q9jJxnDDQjOfk5QA2iP/yyJfbXciwoZgLss8JN4cc1zgEhBoCg1wVdvZIGstEKfpjs4c8jEzHFtJalN3oUp7A");
  }
}

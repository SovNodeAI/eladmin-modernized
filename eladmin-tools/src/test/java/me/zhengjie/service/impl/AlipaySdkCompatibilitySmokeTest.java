/*
 *  Copyright 2026 SovNode
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package me.zhengjie.service.impl;

import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayResponse;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 支付宝 SDK 兼容性冒烟测试：用本地生成的测试密钥直接驱动 AlipayClient 的
 * pageExecute 本地签名与 rsaCheckV2 验签，不访问网络、不使用真实凭据。
 * 它不经过 AliPayServiceImpl（服务实现不可注入 client 工厂），验证的是
 * 依赖坐标治理（移除 fastjson 1.x，更新 dom4j 与 bcprov）前后 SDK 本身可用。
 * 依赖坐标治理前后该测试必须同样通过。
 *
 * @author SovNode
 */
@org.junit.jupiter.api.Tag("behavioral")
class AlipaySdkCompatibilitySmokeTest {

    private static String privateKey;
    private static String publicKey;

    @BeforeAll
    static void generateTestKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
    }

    private static AlipayClient client() {
        return new DefaultAlipayClient(
                "https://openapi.alipay.com/gateway.do",
                "test-app-id",
                privateKey,
                "json",
                "UTF-8",
                publicKey,
                "RSA2");
    }

    @Test
    void pagePaySignsLocallyAndTheSignatureVerifies() throws Exception {
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setReturnUrl("https://example.eladmin.vip/return");
        request.setNotifyUrl("https://example.eladmin.vip/api/aliPay/notify");
        request.setBizContent("{" +
                "    \"out_trade_no\":\"SOV-260-TEST\"," +
                "    \"product_code\":\"FAST_INSTANT_TRADE_PAY\"," +
                "    \"total_amount\":1.00," +
                "    \"subject\":\"local signing behavior test\"," +
                "    \"body\":\"no network involved\"" +
                "  }");

        AlipayResponse response = client().pageExecute(request, "GET");
        String body = response.getBody();
        assertNotNull(body, "pageExecute must build the signed redirect locally");
        assertTrue(body.contains("sign="), "the redirect must carry a local signature");

        Map<String, String> params = new HashMap<>(16);
        String query = body.substring(body.indexOf('?') + 1);
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            params.put(pair.substring(0, eq),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        // pageExecute signs the sorted params including sign_type, which is the
        // rsaCheckV2 content contract; V1 excludes sign_type and also mutates the map.
        assertTrue(com.alipay.api.internal.util.AlipaySignature.rsaCheckV2(
                        new HashMap<>(params), publicKey, "UTF-8", "RSA2"),
                "the local key pair must verify the signature it produced");
    }
}

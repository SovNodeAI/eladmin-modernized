/*
 *  Copyright 2026 SovNode (alipay secret retention on update, SOV-1319); see NOTICE.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package me.zhengjie.service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import me.zhengjie.domain.AlipayConfig;
import java.io.Serializable;

/**
 * 支付宝配置更新入参：privateKey 留空或回传掩码哨兵表示沿用已存私钥，
 * 配合脱敏读视图避免私钥回显后被原样提交覆盖，哨兵永远不会被持久化。
 * @author SovNode
 * @date 2026-08-31
 */
@Getter
@Setter
public class AlipayConfigUpdateDto implements Serializable {

    @NotBlank
    private String appId;

    private String privateKey;

    @NotBlank
    private String publicKey;

    private String signType = "RSA2";

    private String gatewayUrl = "https://openapi.alipaydev.com/gateway.do";

    private String charset = "utf-8";

    @NotBlank
    private String notifyUrl;

    @NotBlank
    private String returnUrl;

    private String format = "JSON";

    @NotBlank
    private String sysServiceProviderId;

    public AlipayConfig toConfigMergingSecretFrom(AlipayConfig stored) {
        AlipayConfig config = new AlipayConfig();
        config.setId(1L);
        config.setAppId(appId);
        config.setPrivateKey(SecretMask.isRetained(privateKey) ? stored.getPrivateKey() : privateKey);
        config.setPublicKey(publicKey);
        config.setSignType(signType);
        config.setGatewayUrl(gatewayUrl);
        config.setCharset(charset);
        config.setNotifyUrl(notifyUrl);
        config.setReturnUrl(returnUrl);
        config.setFormat(format);
        config.setSysServiceProviderId(sysServiceProviderId);
        return config;
    }
}

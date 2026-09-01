/*
 *  Copyright 2026 SovNode (alipay secret redaction, SOV-1319); see NOTICE.
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

import lombok.Getter;
import lombok.Setter;
import me.zhengjie.domain.AlipayConfig;
import java.io.Serializable;

/**
 * 支付宝配置的脱敏读视图：privateKey 永远只以掩码哨兵占位，绝不携带真实商户私钥，
 * 供 GET /api/aliPay 使用，与持久化实体严格分离。
 * @author SovNode
 * @date 2026-08-31
 */
@Getter
@Setter
public class AlipayConfigRedactedDto implements Serializable {

    private Long id;

    private String appId;

    private String privateKey;

    private String publicKey;

    private String signType;

    private String gatewayUrl;

    private String charset;

    private String notifyUrl;

    private String returnUrl;

    private String format;

    private String sysServiceProviderId;

    public static AlipayConfigRedactedDto from(AlipayConfig config) {
        AlipayConfigRedactedDto dto = new AlipayConfigRedactedDto();
        dto.setId(config.getId());
        dto.setAppId(config.getAppId());
        dto.setPrivateKey(SecretMask.mask(config.getPrivateKey()));
        dto.setPublicKey(config.getPublicKey());
        dto.setSignType(config.getSignType());
        dto.setGatewayUrl(config.getGatewayUrl());
        dto.setCharset(config.getCharset());
        dto.setNotifyUrl(config.getNotifyUrl());
        dto.setReturnUrl(config.getReturnUrl());
        dto.setFormat(config.getFormat());
        dto.setSysServiceProviderId(config.getSysServiceProviderId());
        return dto;
    }
}

/*
 *  Copyright 2026 SovNode (email secret redaction, SOV-1319); see NOTICE.
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
import me.zhengjie.domain.EmailConfig;
import java.io.Serializable;

/**
 * 邮件配置的脱敏读视图：pass 永远只以掩码哨兵占位，绝不携带真实邮箱密码，
 * 供 GET /api/email 使用，与持久化实体严格分离。
 * @author SovNode
 * @date 2026-08-31
 */
@Getter
@Setter
public class EmailConfigRedactedDto implements Serializable {

    private Long id;

    private String host;

    private String port;

    private String user;

    private String pass;

    private String fromUser;

    public static EmailConfigRedactedDto from(EmailConfig config) {
        EmailConfigRedactedDto dto = new EmailConfigRedactedDto();
        dto.setId(config.getId());
        dto.setHost(config.getHost());
        dto.setPort(config.getPort());
        dto.setUser(config.getUser());
        dto.setPass(SecretMask.mask(config.getPass()));
        dto.setFromUser(config.getFromUser());
        return dto;
    }
}

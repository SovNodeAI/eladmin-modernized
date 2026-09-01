/*
 *  Copyright 2026 SovNode (email secret retention on update, SOV-1319); see NOTICE.
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
import me.zhengjie.domain.EmailConfig;
import java.io.Serializable;

/**
 * 邮件配置更新入参：pass 留空或回传掩码哨兵表示沿用已存密码，
 * 配合脱敏读视图避免密码回显后被原样提交覆盖，哨兵永远不会被持久化。
 * @author SovNode
 * @date 2026-08-31
 */
@Getter
@Setter
public class EmailConfigUpdateDto implements Serializable {

    @NotBlank
    private String host;

    @NotBlank
    private String port;

    @NotBlank
    private String user;

    private String pass;

    @NotBlank
    private String fromUser;

    public EmailConfig toConfigMergingSecretFrom(EmailConfig stored) {
        EmailConfig config = new EmailConfig();
        config.setId(1L);
        config.setHost(host);
        config.setPort(port);
        config.setUser(user);
        config.setPass(SecretMask.isRetained(pass) ? stored.getPass() : pass);
        config.setFromUser(fromUser);
        return config;
    }
}

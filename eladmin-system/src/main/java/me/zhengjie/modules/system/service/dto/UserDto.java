/*
 *  Copyright 2019-2025 Zheng Jie
 *  Modified by SovNode in 2026 (Spring Boot 4.1 modernization); see NOTICE.
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
package me.zhengjie.modules.system.service.dto;

import com.alibaba.fastjson2.annotation.JSONField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import me.zhengjie.base.BaseDTO;
import java.io.Serializable;
import java.util.Date;
import java.util.Set;

/**
 * @author Zheng Jie
 * @date 2018-11-23
 */
@Getter
@Setter
public class UserDto extends BaseDTO implements Serializable {

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "角色")
    private Set<RoleSmallDto> roles;

    @Schema(description = "岗位")
    private Set<JobSmallDto> jobs;

    @Schema(description = "部门")
    private DeptSmallDto dept;

    @Schema(description = "部门ID")
    private Long deptId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "昵称")
    private String nickName;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "电话")
    private String phone;

    @Schema(description = "性别")
    private String gender;

    @Schema(description = "头像")
    private String avatarName;

    @Schema(description = "头像路径")
    private String avatarPath;

    @Schema(description = "密码")
    @JSONField(serialize = false)
    private String password;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "管理员")
    @JSONField(serialize = false)
    private Boolean isAdmin = false;

    @Schema(description = "密码重置时间")
    private Date pwdResetTime;
}

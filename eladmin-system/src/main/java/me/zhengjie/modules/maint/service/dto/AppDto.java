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
package me.zhengjie.modules.maint.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import me.zhengjie.base.BaseDTO;
import java.io.Serializable;

/**
* @author zhanghouying
* @date 2019-08-24
*/
@Getter
@Setter
public class AppDto extends BaseDTO implements Serializable {

	@Schema(description = "ID")
    private Long id;

	@Schema(description = "应用名称")
	private String name;

	@Schema(description = "端口")
	private Integer port;

	@Schema(description = "上传目录")
	private String uploadPath;

	@Schema(description = "部署目录")
	private String deployPath;

	@Schema(description = "备份目录")
	private String backupPath;

	@Schema(description = "启动脚本")
	private String startScript;

	@Schema(description = "部署脚本")
	private String deployScript;
}

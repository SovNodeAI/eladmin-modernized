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
package me.zhengjie.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import java.sql.Date;
import java.sql.Timestamp;

/**
 * 交易详情，按需应该存入数据库，这里存入数据库，仅供临时测试
 * @author Zheng Jie
 * @date 2018-12-31
 */
@Data
public class TradeVo {

    @NotBlank
    @Schema(description = "商品描述")
    private String body;

    @NotBlank
    @Schema(description = "商品名称")
    private String subject;

    @Schema(description = "商户订单号", hidden = true)
    private String outTradeNo;

    @Schema(description = "第三方订单号", hidden = true)
    private String tradeNo;

    @NotBlank
    @Schema(description = "价格")
    private String totalAmount;

    @Schema(description = "订单状态,已支付，未支付，作废", hidden = true)
    private String state;

    @Schema(description = "创建时间", hidden = true)
    private Timestamp createTime;

    @Schema(description = "作废时间", hidden = true)
    private Date cancelTime;
}

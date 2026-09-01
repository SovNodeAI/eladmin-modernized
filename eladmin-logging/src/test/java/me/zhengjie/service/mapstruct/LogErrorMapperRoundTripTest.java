/*
 *  Copyright 2019-2025 Zheng Jie
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
package me.zhengjie.service.mapstruct;

import me.zhengjie.domain.SysLog;
import me.zhengjie.service.dto.SysLogErrorDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * MapStruct 1.6.3 生成代码的行为验证（logging 模块）：逐字段断言
 * LogErrorMapper 的 toDto / toEntity 往返。
 *
 * @author Zheng Jie
 */
@Tag("behavioral")
class LogErrorMapperRoundTripTest {

    private final LogErrorMapper mapper = new LogErrorMapperImpl();

    private SysLog fullyPopulatedErrorLog() {
        SysLog log = new SysLog();
        log.setId(21L);
        log.setUsername("admin");
        log.setDescription("查询用户列表失败");
        log.setMethod("UserController#query");
        log.setParams("{\"page\":1}");
        log.setLogType("ERROR");
        log.setRequestIp("127.0.0.1");
        log.setAddress("内网IP");
        log.setBrowser("Chrome 126");
        log.setTime(300L);
        log.setExceptionDetail("stack trace bytes".getBytes());
        log.setCreateTime(new java.sql.Timestamp(1_700_000_456_000L));
        return log;
    }

    @Test
    void toDtoCopiesEveryField() {
        SysLogErrorDto dto = mapper.toDto(fullyPopulatedErrorLog());

        assertEquals(21L, dto.getId());
        assertEquals("admin", dto.getUsername());
        assertEquals("查询用户列表失败", dto.getDescription());
        assertEquals("UserController#query", dto.getMethod());
        assertEquals("{\"page\":1}", dto.getParams());
        assertEquals("Chrome 126", dto.getBrowser());
        assertEquals("127.0.0.1", dto.getRequestIp());
        assertEquals("内网IP", dto.getAddress());
        assertEquals(new java.sql.Timestamp(1_700_000_456_000L), dto.getCreateTime());
    }

    @Test
    void toEntityCopiesEveryFieldBack() {
        SysLog entity = mapper.toEntity(mapper.toDto(fullyPopulatedErrorLog()));

        assertEquals(21L, entity.getId());
        assertEquals("admin", entity.getUsername());
        assertEquals("查询用户列表失败", entity.getDescription());
        assertEquals("UserController#query", entity.getMethod());
        assertEquals("{\"page\":1}", entity.getParams());
        assertEquals("Chrome 126", entity.getBrowser());
        assertEquals("127.0.0.1", entity.getRequestIp());
        assertEquals("内网IP", entity.getAddress());
        assertEquals(new java.sql.Timestamp(1_700_000_456_000L), entity.getCreateTime());
    }

    @Test
    void collectionOverloadsMapElementWise() {
        List<SysLog> logs = List.of(fullyPopulatedErrorLog());
        assertEquals(1, mapper.toDto(logs).size());
        assertEquals(1, mapper.toEntity(mapper.toDto(logs)).size());
    }

    @Test
    void generatedImplementationIsWiredForSpringComponentModel() {
        assertNotNull(LogErrorMapperImpl.class.getAnnotation(
                org.springframework.stereotype.Component.class));
    }
}

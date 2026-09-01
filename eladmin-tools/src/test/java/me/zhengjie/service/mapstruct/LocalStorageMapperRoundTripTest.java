/*
 *  Copyright 2026 SovNode
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

import me.zhengjie.domain.LocalStorage;
import me.zhengjie.service.dto.LocalStorageDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * MapStruct 1.6.3 生成代码的行为验证（tools 模块）：逐字段断言
 * LocalStorageMapper 的 toDto / toEntity 往返，处理器若与 Lombok
 * 脱节会在本测试下产生空值断言失败。
 *
 * @author SovNode
 */
@Tag("behavioral")
class LocalStorageMapperRoundTripTest {

    private final LocalStorageMapper mapper = new LocalStorageMapperImpl();

    private LocalStorage fullyPopulatedStorage() {
        LocalStorage storage = new LocalStorage();
        storage.setId(11L);
        storage.setRealName("eladmin手册.pdf");
        storage.setName("手册");
        storage.setSuffix("pdf");
        storage.setPath("/storage/2026/08/manual.pdf");
        storage.setType("文档");
        storage.setSize("1024");
        storage.setCreateTime(new Timestamp(1_700_000_123_000L));
        return storage;
    }

    @Test
    void toDtoCopiesEveryField() {
        LocalStorageDto dto = mapper.toDto(fullyPopulatedStorage());

        assertEquals(11L, dto.getId());
        assertEquals("eladmin手册.pdf", dto.getRealName());
        assertEquals("手册", dto.getName());
        assertEquals("pdf", dto.getSuffix());
        assertEquals("文档", dto.getType());
        assertEquals("1024", dto.getSize());
    }

    @Test
    void toEntityCopiesEveryFieldBack() {
        LocalStorage entity = mapper.toEntity(mapper.toDto(fullyPopulatedStorage()));

        assertEquals(11L, entity.getId());
        assertEquals("eladmin手册.pdf", entity.getRealName());
        assertEquals("手册", entity.getName());
        assertEquals("pdf", entity.getSuffix());
        assertEquals("文档", entity.getType());
        assertEquals("1024", entity.getSize());
        // DTO 不携带 path 字段，往返后实体 path 为空是该映射契约的一部分
        assertNull(entity.getPath());
    }

    @Test
    void collectionOverloadsMapElementWise() {
        List<LocalStorage> storages = List.of(fullyPopulatedStorage());
        assertEquals(1, mapper.toDto(storages).size());
        assertEquals(1, mapper.toEntity(mapper.toDto(storages)).size());
    }

    @Test
    void generatedImplementationIsWiredForSpringComponentModel() {
        assertNotNull(LocalStorageMapperImpl.class.getAnnotation(
                org.springframework.stereotype.Component.class));
    }
}

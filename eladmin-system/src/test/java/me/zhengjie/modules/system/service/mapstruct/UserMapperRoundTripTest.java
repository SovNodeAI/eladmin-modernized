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
package me.zhengjie.modules.system.service.mapstruct;

import me.zhengjie.modules.system.domain.Dept;
import me.zhengjie.modules.system.domain.Job;
import me.zhengjie.modules.system.domain.Role;
import me.zhengjie.modules.system.domain.User;
import me.zhengjie.modules.system.service.dto.DeptSmallDto;
import me.zhengjie.modules.system.service.dto.JobSmallDto;
import me.zhengjie.modules.system.service.dto.RoleSmallDto;
import me.zhengjie.modules.system.service.dto.UserDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * MapStruct 1.6.3 生成代码的行为验证：User 的访问器由 Lombok 生成，
 * 若处理器未与 Lombok 协作，会生成编译通过但字段全空的映射器。
 * 本测试逐字段断言 toDto 与 toEntity 的往返值，把该静默失败变成红灯。
 *
 * @author Zheng Jie
 */
@Tag("behavioral")
class UserMapperRoundTripTest {

    private final UserMapper userMapper = new UserMapperImpl();

    private User fullyPopulatedUser() {
        Role role = new Role();
        role.setId(2L);
        role.setName("超级管理员");
        role.setLevel(1);
        role.setDataScope("all");

        Job job = new Job();
        job.setId(4L);
        job.setName("全栈开发");
        job.setJobSort(9L);

        Dept dept = new Dept();
        dept.setId(3L);
        dept.setName("研发部");

        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setNickName("管理员");
        user.setEmail("admin@eladmin.vip");
        user.setPhone("13800138000");
        user.setGender("男");
        user.setAvatarName("admin.gif");
        user.setAvatarPath("/avatar/admin.gif");
        user.setPassword("desEncryptSecret");
        user.setEnabled(true);
        user.setIsAdmin(true);
        user.setPwdResetTime(new Date(1_700_000_000_000L));
        user.setRoles(Set.of(role));
        user.setJobs(Set.of(job));
        user.setDept(dept);
        return user;
    }

    @Test
    void toDtoCopiesEveryFieldIncludingNestedCollections() {
        UserDto dto = userMapper.toDto(fullyPopulatedUser());

        assertEquals(1L, dto.getId());
        assertEquals("admin", dto.getUsername());
        assertEquals("管理员", dto.getNickName());
        assertEquals("admin@eladmin.vip", dto.getEmail());
        assertEquals("13800138000", dto.getPhone());
        assertEquals("男", dto.getGender());
        assertEquals("admin.gif", dto.getAvatarName());
        assertEquals("/avatar/admin.gif", dto.getAvatarPath());
        assertEquals("desEncryptSecret", dto.getPassword());
        assertEquals(true, dto.getEnabled());
        assertEquals(true, dto.getIsAdmin());
        assertEquals(new Date(1_700_000_000_000L), dto.getPwdResetTime());

        assertNotNull(dto.getDept());
        DeptSmallDto dept = dto.getDept();
        assertEquals(3L, dept.getId());
        assertEquals("研发部", dept.getName());
        // deptId 是 UserDto 上的独立字段，生产代码与生成映射器都不从 dept 派生它，
        // 该契约在此固定为 null
        assertNull(dto.getDeptId());

        assertEquals(1, dto.getRoles().size());
        RoleSmallDto role = dto.getRoles().iterator().next();
        assertEquals(2L, role.getId());
        assertEquals("超级管理员", role.getName());
        assertEquals(1, role.getLevel());
        assertEquals("all", role.getDataScope());

        assertEquals(1, dto.getJobs().size());
        JobSmallDto job = dto.getJobs().iterator().next();
        assertEquals(4L, job.getId());
        assertEquals("全栈开发", job.getName());
    }

    @Test
    void toEntityCopiesEveryFieldBack() {
        UserDto dto = userMapper.toDto(fullyPopulatedUser());
        User user = userMapper.toEntity(dto);

        assertEquals(1L, user.getId());
        assertEquals("admin", user.getUsername());
        assertEquals("管理员", user.getNickName());
        assertEquals("admin@eladmin.vip", user.getEmail());
        assertEquals("13800138000", user.getPhone());
        assertEquals("男", user.getGender());
        assertEquals("admin.gif", user.getAvatarName());
        assertEquals("/avatar/admin.gif", user.getAvatarPath());
        assertEquals("desEncryptSecret", user.getPassword());
        assertEquals(true, user.getEnabled());
        assertEquals(true, user.getIsAdmin());
        assertEquals(new Date(1_700_000_000_000L), user.getPwdResetTime());

        assertNotNull(user.getDept());
        assertEquals(3L, user.getDept().getId());
        assertEquals("研发部", user.getDept().getName());
        assertEquals(1, user.getRoles().size());
        assertEquals(2L, user.getRoles().iterator().next().getId());
        assertEquals(1, user.getJobs().size());
        assertEquals(4L, user.getJobs().iterator().next().getId());
    }

    @Test
    void collectionOverloadsMapElementWise() {
        assertEquals(1, userMapper.toDto(List.of(fullyPopulatedUser())).size());
        assertEquals(1, userMapper.toEntity(List.of(userMapper.toDto(fullyPopulatedUser()))).size());
    }

    @Test
    void generatedImplementationIsWiredForSpringComponentModel() {
        assertNotNull(UserMapperImpl.class.getAnnotation(org.springframework.stereotype.Component.class),
                "componentModel spring must put the generated mapper under component scanning");
    }
}

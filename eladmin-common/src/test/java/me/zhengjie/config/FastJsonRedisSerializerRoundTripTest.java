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
package me.zhengjie.config;

import lombok.Data;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redis 序列化行为测试：通过生产 RedisConfiguration.redisTemplate 构建的真实
 * 模板序列化器做值往返。FastJsonRedisSerializer 以 WriteClassName 写出 @type，
 * me.zhengjie 白名单在模板构建时注册；往返断裂或 @type 丢失都会让本测试变红。
 *
 * @author Zheng Jie
 */
@Tag("behavioral")
class FastJsonRedisSerializerRoundTripTest {

    @Data
    public static class Payload {
        private Long id;
        private String name;
        private Boolean enabled;
        private Date createTime;
    }

    private static RedisTemplate<Object, Object> productionTemplate() {
        return new RedisConfiguration().redisTemplate(
                Mockito.mock(RedisConnectionFactory.class));
    }

    @SuppressWarnings("unchecked")
    private static RedisSerializer<Object> valueSerializer(RedisTemplate<Object, Object> template) {
        return (RedisSerializer<Object>) template.getValueSerializer();
    }

    private static Payload fullyPopulatedPayload() {
        Payload payload = new Payload();
        payload.setId(31L);
        payload.setName("eladmin缓存值");
        payload.setEnabled(Boolean.TRUE);
        payload.setCreateTime(new Date(1_700_000_789_000L));
        return payload;
    }

    @Test
    void valueSerializerKeepsWriteClassNameWireFormat() {
        byte[] bytes = valueSerializer(productionTemplate()).serialize(fullyPopulatedPayload());
        String json = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(json.startsWith("{\"@type\":\"me.zhengjie.config.FastJsonRedisSerializerRoundTripTest$Payload\""),
                "the @type prefix is the wire contract existing Redis data relies on, got: " + json);
        assertTrue(json.contains("\"id\":31"));
        assertTrue(json.contains("\"name\":\"eladmin缓存值\""));
    }

    @Test
    void valueRoundTripsThroughTheConfiguredSerializer() {
        RedisTemplate<Object, Object> template = productionTemplate();
        RedisSerializer<Object> serializer = valueSerializer(template);
        Object back = serializer.deserialize(serializer.serialize(fullyPopulatedPayload()));

        Payload payload = assertInstanceOf(Payload.class, back);
        assertEquals(Long.valueOf(31L), payload.getId());
        assertEquals("eladmin缓存值", payload.getName());
        assertEquals(Boolean.TRUE, payload.getEnabled());
        assertEquals(new Date(1_700_000_789_000L), payload.getCreateTime());
    }

    @Test
    void keysStayStringSerialized() {
        RedisTemplate<Object, Object> template = productionTemplate();
        assertInstanceOf(StringRedisSerializer.class, template.getKeySerializer());
        @SuppressWarnings("rawtypes")
        RedisSerializer rawKeySerializer = template.getKeySerializer();
        assertArrayEquals("email::config".getBytes(StandardCharsets.UTF_8),
                (byte[]) rawKeySerializer.serialize("email::config"));
    }
}

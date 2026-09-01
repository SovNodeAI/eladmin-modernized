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
package me.zhengjie.config.webConfig;

import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;
import lombok.Data;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.http.MockHttpOutputMessage;
import me.zhengjie.config.properties.FileProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * fastjson2 HTTP JSON 形状特征测试：通过生产代码 ConfigurerAdapter 实际注册的
 * FastJsonHttpMessageConverter 序列化代表性负载。fastjson2-extension-spring6 在
 * Framework 7 上靠二进制兼容工作，注册入口 configureMessageConverters 已被标记
 * forRemoval；若未来该转换器被静默丢掉（Jackson 兜底输出不同形状），本测试变红。
 *
 * @author Zheng Jie
 */
@Tag("behavioral")
class FastJsonConverterShapeCharacterizationTest {

    private static final MediaType APPLICATION_JSON_UTF8 =
            new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);

    private static final long EPOCH_MILLIS = 1_756_082_096_000L;

    /**
     * fastjson2 按运行 JVM 默认时区格式化日期；期望值用同一时区推导，
     * 避免测试在不同时区的机器上误报。
     */
    private static String expectedTimestamp() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(EPOCH_MILLIS));
    }

    @Data
    static class RepresentativePayload {
        private Long id;
        private String name;
        private Integer count;
        private Date createTime;
        private String remark;
        private List<String> tags;
        private Object extra;
    }

    private static FastJsonHttpMessageConverter registeredConverter() {
        ConfigurerAdapter adapter = new ConfigurerAdapter(new FileProperties());
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        adapter.configureMessageConverters(converters);
        assertEquals(1, converters.size(),
                "ConfigurerAdapter must register exactly one converter");
        return (FastJsonHttpMessageConverter) converters.get(0);
    }

    private static RepresentativePayload fullyPopulatedPayload() {
        RepresentativePayload payload = new RepresentativePayload();
        payload.setId(9_007_199_254_740_993L);
        payload.setName("eladmin");
        payload.setCount(7);
        payload.setCreateTime(new Date(EPOCH_MILLIS));
        payload.setRemark(null);
        payload.setTags(List.of("boot4", "java21"));
        return payload;
    }

    @Test
    void serializedShapePinsDatesLongsAndOmittedNulls() throws IOException {
        FastJsonHttpMessageConverter converter = registeredConverter();
        MockHttpOutputMessage output = new MockHttpOutputMessage();
        converter.write(fullyPopulatedPayload(), APPLICATION_JSON_UTF8, output);

        assertEquals("application/json;charset=UTF-8", output.getHeaders().getContentType().toString());
        String json = output.getBodyAsString(StandardCharsets.UTF_8);
        assertEquals("{\"count\":7,\"createTime\":\"" + expectedTimestamp() + "\","
                        + "\"id\":9007199254740993,\"name\":\"eladmin\","
                        + "\"tags\":[\"boot4\",\"java21\"]}",
                json,
                "the JSON shape the frontend depends on must stay byte stable");
    }

    @Test
    void referenceDetectionStaysEnabled() throws IOException {
        FastJsonHttpMessageConverter converter = registeredConverter();
        RepresentativePayload payload = fullyPopulatedPayload();
        List<String> shared = new ArrayList<>(List.of("boot4", "java21"));
        payload.setTags(shared);
        payload.setExtra(shared);

        MockHttpOutputMessage output = new MockHttpOutputMessage();
        converter.write(payload, APPLICATION_JSON_UTF8, output);
        String json = output.getBodyAsString(StandardCharsets.UTF_8);

        assertTrue(json.contains("\"$ref\""),
                "ReferenceDetection is configured in production and a repeated object "
                        + "must stay reference encoded, got: " + json);
    }

    @Test
    void deserializationRoundTripsThroughTheSameConverter() throws IOException {
        FastJsonHttpMessageConverter converter = registeredConverter();
        MockHttpOutputMessage output = new MockHttpOutputMessage();
        converter.write(fullyPopulatedPayload(), APPLICATION_JSON_UTF8, output);

        @SuppressWarnings("rawtypes")
        HttpMessageConverter rawConverter = converter;
        RepresentativePayload read = (RepresentativePayload) rawConverter.read(
                RepresentativePayload.class,
                new MockHttpInputMessage(output.getBodyAsBytes()));
        assertEquals(Long.valueOf(9_007_199_254_740_993L), read.getId());
        assertEquals("eladmin", read.getName());
        assertEquals(Integer.valueOf(7), read.getCount());
        assertEquals(new Date(EPOCH_MILLIS), read.getCreateTime());
        assertEquals(List.of("boot4", "java21"), read.getTags());
    }
}

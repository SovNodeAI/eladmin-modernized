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
package me.zhengjie.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * sql字段转java
 *
 * @author Zheng Jie
 * @date 2019-01-03
 */
public class ColUtil {
    private static final String CONFIG_RESOURCE = "gen.properties";
    private static final Properties CONFIG = loadConfig();

    /**
     * 转换mysql数据类型为java数据类型
     *
     * @param type 数据库字段类型
     * @return String
     */
    static String cloToJava(String type) {
        return CONFIG.getProperty(type, "unknowType");
    }

    /**
     * 获取配置信息
     */
    public static Properties getConfig() {
        Properties copy = new Properties();
        copy.putAll(CONFIG);
        return copy;
    }

    private static Properties loadConfig() {
        try (InputStream stream = ColUtil.class.getClassLoader().getResourceAsStream(CONFIG_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing generator configuration: " + CONFIG_RESOURCE);
            }
            Properties properties = new Properties();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load generator configuration: " + CONFIG_RESOURCE, e);
        }
    }
}

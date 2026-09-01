/*
 *  Copyright 2026 SovNode (security slice A configuration hardening); see NOTICE.
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
package me.zhengjie;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 安全切片 A 的回归守卫：仓库跟踪文件只允许携带环境占位符，
 * 种子数据不得携带可用凭据。任何一项回退都会让本类失败。
 */
@Tag("static-contract")
public class ShippedSecretsAbsentTest {

    private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}");

    private static String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }

    private static String valueOfKey(String text, String key) {
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(key)) {
                return trimmed.substring(key.length()).trim();
            }
        }
        return null;
    }

    @Test
    public void configFilesCarryPlaceholdersOnly() throws IOException {
        String main = read("src/main/resources/config/application.yml");
        String dev = read("src/main/resources/config/application-dev.yml");
        String prod = read("src/main/resources/config/application-prod.yml");
        assertEquals("${SPRING_PROFILES_ACTIVE:}", valueOfKey(main, "active:"),
                "the packaged launch must select the profile only from the environment");
        assertEquals("${RSA_PRIVATE_KEY}", valueOfKey(main, "private_key:"));
        assertEquals("${JWT_SECRET}", valueOfKey(dev, "base64-secret:"));
        assertEquals("${JWT_SECRET}", valueOfKey(prod, "base64-secret:"));
        assertEquals("${DB_PWD:}", valueOfKey(dev, "password:"));
        assertEquals("${DB_PWD}", valueOfKey(prod, "password:"),
                "prod must fail closed on a missing DB password instead of an empty default");
        assertTrue(prod.lines().anyMatch(l -> l.trim().equals("password: ${REDIS_PWD}")),
                "prod must require the Redis password via a defaultless placeholder");
        assertFalse(prod.contains("${REDIS_PWD:}"),
                "prod must not accept an empty Redis password fallback");
        assertEquals("${S3_SECRET_KEY:}", valueOfKey(dev, "secretKey:"));
        assertEquals("${S3_SECRET_KEY:}", valueOfKey(prod, "secretKey:"));
    }

    @Test
    public void seedSqlShipsNoUsableCredential() throws IOException {
        Path sql = Path.of("../sql/eladmin.sql");
        assertTrue(Files.exists(sql), "seed sql must be reachable from the module directory");
        String text = read("../sql/eladmin.sql");
        assertFalse(BCRYPT.matcher(text).find(), "seed sql must not ship bcrypt hashes");
        assertFalse(text.contains("/Users/"), "seed sql must not ship developer local paths");
        assertTrue(text.contains("__LOCAL_ONBOARDING__"), "seed rows must require onboarding activation");
        String testRow = null;
        for (String line : text.split("\n")) {
            if (line.contains("INSERT INTO `sys_user`") && line.contains("VALUES (2, ")) {
                testRow = line;
            }
        }
        assertTrue(testRow != null, "seed test account row must exist");
        assertTrue(testRow.contains(", b'0', b'0',"), "seed test account must ship disabled");
    }
}

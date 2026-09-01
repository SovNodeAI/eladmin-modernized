/*
 * Copyright 2026 SovNode
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.zhengjie.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@org.junit.jupiter.api.Tag("behavioral")
class ColUtilTest {

    @Test
    void databaseTypesLoadFromThePackagedConfiguration() {
        assertEquals("Integer", ColUtil.cloToJava("int"));
        assertEquals("Long", ColUtil.cloToJava("bigint"));
        assertEquals("Timestamp", ColUtil.cloToJava("datetime"));
    }

    @Test
    void unknownDatabaseTypesKeepTheExistingFallback() {
        assertEquals("unknowType", ColUtil.cloToJava("made_up_type"));
    }

    @Test
    void legacyCommonsConfigurationIsAbsentFromTheRuntimeClasspath() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.apache.commons.configuration.PropertiesConfiguration", false,
                        ColUtilTest.class.getClassLoader()),
                "Commons Configuration 1.x must not be present on the generator runtime classpath");
    }
}

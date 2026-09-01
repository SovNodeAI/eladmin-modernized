/*
 *  Copyright 2026 SovNode
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

import org.junit.jupiter.api.Test;

import java.security.CodeSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 支付宝依赖图契约测试：alipay sdk 的遗留 fastjson 1.x 依赖不得进入类路径，
 * dom4j 与 Bouncy Castle 必须落在维护中的坐标。
 *
 * @author SovNode
 */
@org.junit.jupiter.api.Tag("behavioral")
class AlipayDependencyGraphContractTest {

    private static String jarOf(String className) throws Exception {
        Class<?> clazz = Class.forName(className, false,
                AlipayDependencyGraphContractTest.class.getClassLoader());
        CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
        assertNotNull(codeSource, className + " must come from a jar, not a directory");
        return codeSource.getLocation().toString();
    }

    @Test
    void legacyFastjsonIsAbsentFromTheRuntimeClasspath() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.alibaba.fastjson.JSON", false,
                        AlipayDependencyGraphContractTest.class.getClassLoader()),
                "fastjson 1.x must not be present on the Alipay runtime classpath");
    }

    @Test
    void dom4jStaysOnTheMaintainedJakartaFriendlyLine() throws Exception {
        assertTrue(jarOf("org.dom4j.DocumentHelper").endsWith("dom4j-2.2.0.jar"),
                "dom4j must resolve to the maintained org.dom4j 2.2.0 line");
    }

    @Test
    void bouncyCastleStaysOnTheCurrentJdkLine() throws Exception {
        assertTrue(jarOf("org.bouncycastle.jce.provider.BouncyCastleProvider")
                        .endsWith("bcprov-jdk18on-1.85.2.jar"),
                "bcprov must resolve to the jdk18on 1.85.2 line");
    }
}

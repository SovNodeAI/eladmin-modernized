/*
 *  Copyright 2019-2025 Zheng Jie
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 支付宝依赖图契约测试：alipay sdk 的三个遗留传递依赖必须落在修复线上，
 * fastjson 1.2.83_noneautotype、dom4j 2.2.0、bcprov jdk18on，旧坐标不得再出现在类路径。
 *
 * @author Zheng Jie
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
    void fastjsonStaysOnThePatchedTerminalLine() throws Exception {
        assertTrue(jarOf("com.alibaba.fastjson.JSON").endsWith("fastjson-1.2.83_noneautotype.jar"),
                "fastjson must resolve to 1.2.83_noneautotype, the patched terminal 1.2.x release with autotype disabled");
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

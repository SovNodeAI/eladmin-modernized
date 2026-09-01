/*
 *  Modified by SovNode in 2026 (Spring Boot 4.1 modernization); see NOTICE.
 */
package me.zhengjie.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static me.zhengjie.utils.EncryptUtils.desDecrypt;
import static me.zhengjie.utils.EncryptUtils.desEncrypt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DES 材料改为运行时注入后的行为测试：显式材料可往返、密文依赖材料、
 * 缺失材料时快速失败。不再断言出厂密钥下的固定密文。
 */
@Tag("behavioral")
public class EncryptUtilsTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @AfterEach
    public void resetMaterial() {
        EncryptUtils.desMaterialOverride = null;
    }

    private static String material() {
        StringBuilder builder = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            builder.append((char) ('a' + RANDOM.nextInt(26)));
        }
        return builder.toString();
    }

    @Test
    public void roundTripsWithExplicitMaterial() throws Exception {
        EncryptUtils.desMaterialOverride = material();
        String plain = "eladmin-des-roundtrip";
        String cipher = desEncrypt(plain);
        assertNotEquals(plain, cipher);
        assertEquals(plain, desDecrypt(cipher));
    }

    @Test
    public void ciphertextDependsOnMaterial() throws Exception {
        String plain = "eladmin-des-keycheck";
        EncryptUtils.desMaterialOverride = material();
        String first = desEncrypt(plain);
        EncryptUtils.desMaterialOverride = material();
        String second = desEncrypt(plain);
        assertNotEquals(first, second);
    }

    @Test
    public void missingMaterialFailsClosed() {
        EncryptUtils.desMaterialOverride = "";
        assertThrows(IllegalStateException.class, () -> desEncrypt("eladmin-des-missing"));
    }
}

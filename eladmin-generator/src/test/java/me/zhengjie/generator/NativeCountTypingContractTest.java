package me.zhengjie.generator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hibernate 6 and newer return native count(1) results as Long instead of
 * the BigInteger that Hibernate 5 produced on MySQL, so the generator table
 * listing must read the count through java.lang.Number instead of a hard
 * BigInteger cast. Static source contract, no Spring context and no MySQL.
 *
 * @author Zheng Jie
 */
@org.junit.jupiter.api.Tag("snapshot")
class NativeCountTypingContractTest {

    @Test
    void generatorBindsNativeCountThroughNumberNotBigInteger() throws IOException {
        Path moduleDir = Path.of("").toAbsolutePath().normalize();
        Path root = moduleDir.getParent();
        assertTrue(root != null && Files.isRegularFile(root.resolve("pom.xml")),
                "repository root with pom.xml not found one level above the module");
        Path service = root.resolve(
                "eladmin-generator/src/main/java/me/zhengjie/service/impl/GeneratorServiceImpl.java");
        assertTrue(Files.isRegularFile(service), "GeneratorServiceImpl.java must exist");
        String source = Files.readString(service, StandardCharsets.UTF_8);

        assertFalse(source.contains("(BigInteger) queryCount.getSingleResult()"),
                "native count must not be cast to BigInteger: Hibernate 6 and newer return Long");
        assertFalse(source.contains("BigInteger totalElements"),
                "the count binding must not depend on the Hibernate 5 BigInteger return type");
        assertTrue(source.contains("Number totalElements = (Number) queryCount.getSingleResult()"),
                "the count must be bound through java.lang.Number to survive Long and BigInteger alike");
        assertTrue(source.contains("totalElements.longValue()"),
                "the page total must be derived via longValue so both return types paginate identically");
    }
}

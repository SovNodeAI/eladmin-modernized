/*
 *  Modified by SovNode in 2026 (Spring Boot 4.1 modernization); see NOTICE.
 */
package me.zhengjie.utils;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static me.zhengjie.utils.FileUtil.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@org.junit.jupiter.api.Tag("behavioral")
public class FileUtilTest {

    @Test
    public void testToFile() throws IOException {
        byte[] content = {1, 2, 3};
        File file = toFile(new MockMultipartFile("foo", "foo.txt", "text/plain", content));
        assertNotNull(file);
        try {
            assertTrue(file.isFile());
            assertArrayEquals(content, Files.readAllBytes(file.toPath()));
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    @Test
    public void testGetExtensionName() {
        assertEquals("foo", getExtensionName("foo"));
        assertEquals("exe", getExtensionName("bar.exe"));
    }

    @Test
    public void testGetFileNameNoEx() {
        assertEquals("foo", getFileNameNoEx("foo"));
        assertEquals("bar", getFileNameNoEx("bar.txt"));
    }

    @Test
    public void testGetSize() {
        assertEquals("1000B", getSize(1000));
        assertEquals("1.00KB", getSize(1024));
        assertEquals("1.00MB", getSize(1048576));
        assertEquals("1.00GB", getSize(1073741824));
    }
}

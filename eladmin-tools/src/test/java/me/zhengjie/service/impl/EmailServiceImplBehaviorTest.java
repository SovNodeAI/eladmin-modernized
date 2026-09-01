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
package me.zhengjie.service.impl;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import me.zhengjie.domain.EmailConfig;
import me.zhengjie.domain.vo.EmailVo;
import me.zhengjie.repository.EmailRepository;
import me.zhengjie.utils.EncryptUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 邮件发送行为测试：用进程内 GreenMail SMTPS 服务器真实驱动
 * {@link EmailServiceImpl#send(EmailVo, EmailConfig)} 的完整发送链路
 * （MailAccount 组装、DES 口令解密、jakarta 命名空间的 hutool JakartaMail、
 * TLS 握手、SMTP 认证、消息送达），证明 angus-mail 迁移后类链接成立。
 * 在旧的 javax 命名空间 Mail.create 实现上，本测试以 NoClassDefFoundError 失败。
 *
 * @author Zheng Jie
 */
@Tag("behavioral")
class EmailServiceImplBehaviorTest {

    private static final String SMTP_USER = "sender";
    private static final String SMTP_FROM = "sender@localhost";
    private static final String SMTP_PASSWORD = "greenmail-pass";
    private static final String RECIPIENT = "recipient@localhost";
    private static final String KEYSTORE_PASSWORD = "changeit";
    private static final String TEST_DES_MATERIAL = "test-des";

    private static GreenMail greenMail;
    private static Path tlsDirectory;
    private static Map<String, String> savedProperties;

    @BeforeAll
    static void startSmtpsServerTrustedByThisJvm() throws Exception {
        tlsDirectory = Files.createTempDirectory("eladmin-mail-smtps");
        try {
            Path keystore = tlsDirectory.resolve("greenmail-smtps.p12");
            Path certificate = tlsDirectory.resolve("greenmail-smtps.crt");
            Path truststore = tlsDirectory.resolve("eladmin-smtps-trust.p12");
            generateTrustedSmtpsMaterial(keystore, certificate, truststore);
            savedProperties = new HashMap<>();
            putAndRemember("greenmail.tls.keystore.file", keystore.toString());
            putAndRemember("javax.net.ssl.trustStore", truststore.toString());
            System.setProperty("greenmail.tls.keystore.password", KEYSTORE_PASSWORD);
            System.setProperty("javax.net.ssl.trustStorePassword", KEYSTORE_PASSWORD);
            System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
        } catch (IOException | InterruptedException e) {
            Assumptions.assumeTrue(false,
                    "keytool TLS material could not be generated, mail linkage test skipped: " + e);
            return;
        }

        greenMail = new GreenMail(ServerSetupTest.SMTPS.dynamicPort());
        greenMail.start();
        greenMail.setUser(SMTP_FROM, SMTP_USER, SMTP_PASSWORD);
        putAndRemember("eladmin.des.key", TEST_DES_MATERIAL);
    }

    private static void putAndRemember(String key, String value) {
        savedProperties.put(key, System.setProperty(key, value));
    }

    @AfterAll
    static void stopSmtpsServerAndRestoreJvmSecurityProperties() throws IOException {
        if (greenMail != null) {
            greenMail.stop();
        }
        if (savedProperties != null) {
            savedProperties.forEach((key, previous) -> {
                if (previous == null) {
                    System.clearProperty(key);
                } else {
                    System.setProperty(key, previous);
                }
            });
        }
        System.clearProperty("greenmail.tls.keystore.password");
        System.clearProperty("javax.net.ssl.trustStorePassword");
        System.clearProperty("javax.net.ssl.trustStoreType");
        if (tlsDirectory != null) {
            deleteRecursively(tlsDirectory);
        }
    }

    @Test
    void sendDeliversHtmlMailThroughTheRealSmtpsPath() throws Exception {
        Assumptions.assumeTrue(greenMail != null,
                "GreenMail SMTPS server did not start");

        EmailConfig config = new EmailConfig();
        config.setId(1L);
        config.setHost("localhost");
        config.setPort(String.valueOf(greenMail.getSmtps().getPort()));
        config.setUser("ELADMIN");
        config.setFromUser(SMTP_FROM);
        config.setPass(EncryptUtils.desEncrypt(SMTP_PASSWORD));

        String subject = "eladmin mail linkage check";
        String body = "<p>eladmin mail linkage body</p>";
        EmailVo vo = new EmailVo(List.of(RECIPIENT), subject, body);

        new EmailServiceImpl(Mockito.mock(EmailRepository.class)).send(vo, config);

        assertTrue(greenMail.waitForIncomingEmail(5_000L, 1),
                "the SMTPS server must receive the sent message");
        MimeMessage[] received = greenMail.getReceivedMessages();
        assertEquals(1, received.length, "exactly one message must arrive");
        MimeMessage message = received[0];
        assertEquals(subject, message.getSubject());
        assertNotNull(message.getFrom());
        assertEquals(1, message.getFrom().length);
        assertEquals(SMTP_FROM, ((jakarta.mail.internet.InternetAddress) message.getFrom()[0]).getAddress());
        assertNotNull(message.getAllRecipients());
        assertEquals(1, message.getAllRecipients().length);
        assertEquals(RECIPIENT,
                ((jakarta.mail.internet.InternetAddress) message.getAllRecipients()[0]).getAddress());
        Object content = message.getContent();
        if (content instanceof jakarta.mail.internet.MimeMultipart multipart) {
            assertTrue(multipart.getContentType().toLowerCase().contains("text/html")
                            || multipart.getBodyPart(0).isMimeType("text/html"),
                    "html mail flag must produce an html body part, got: "
                            + multipart.getContentType());
            content = multipart.getBodyPart(0).getContent();
        }
        assertTrue(String.valueOf(content).contains("eladmin mail linkage body"),
                "html body must survive the send path, got: " + content);
    }

    private static void generateTrustedSmtpsMaterial(Path keystore, Path certificate, Path truststore)
            throws IOException, InterruptedException {
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        runKeytool(keytool,
                "-genkeypair", "-alias", "greenmail", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=localhost", "-validity", "7", "-storetype", "PKCS12",
                "-keystore", keystore.toString(), "-storepass", KEYSTORE_PASSWORD);
        runKeytool(keytool,
                "-exportcert", "-rfc", "-alias", "greenmail", "-storetype", "PKCS12",
                "-keystore", keystore.toString(), "-storepass", KEYSTORE_PASSWORD,
                "-file", certificate.toString());
        runKeytool(keytool,
                "-importcert", "-noprompt", "-alias", "greenmail", "-storetype", "PKCS12",
                "-keystore", truststore.toString(), "-storepass", KEYSTORE_PASSWORD,
                "-file", certificate.toString());
    }

    private static void runKeytool(String keytool, String... arguments)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(keytool);
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        String output = new String(process.getInputStream().readAllBytes());
        assertTrue(finished && process.exitValue() == 0,
                "keytool " + List.of(arguments) + " failed: " + output);
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var stream = Files.walk(directory)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException ignored) {
                    // best effort cleanup of temporary TLS material
                }
            });
        }
    }
}

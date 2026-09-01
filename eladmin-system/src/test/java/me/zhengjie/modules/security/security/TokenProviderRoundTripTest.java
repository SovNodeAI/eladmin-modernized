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
package me.zhengjie.modules.security.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import me.zhengjie.modules.security.config.SecurityProperties;
import me.zhengjie.modules.security.service.dto.JwtUserDto;
import me.zhengjie.modules.system.service.dto.UserDto;
import me.zhengjie.utils.RedisUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Token 行为对等性测试：只通过 TokenProvider 公开 API 断言签发与解析契约，
 * 与底层 jjwt 序列化实现无关，序列器更换（jackson 到 gson）前后必须同样通过。
 *
 * @author SovNode
 */
@org.junit.jupiter.api.Tag("behavioral")
class TokenProviderRoundTripTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(
            "round-trip-test-secret-key-that-is-long-enough-for-hs512-signing-0123456789abcdef"
                    .getBytes(StandardCharsets.UTF_8));

    private TokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties();
        properties.setHeader("Authorization");
        properties.setTokenStartWith("Bearer ");
        properties.setBase64Secret(SECRET);
        properties.setOnlineKey("online-token:");
        properties.setTokenValidityInSeconds(86400L);
        tokenProvider = new TokenProvider(properties, Mockito.mock(RedisUtils.class));
        tokenProvider.afterPropertiesSet();
    }

    private JwtUserDto admin() {
        UserDto user = new UserDto();
        user.setId(2L);
        user.setUsername("admin");
        return new JwtUserDto(user, new ArrayList<>(), new ArrayList<>());
    }

    @Test
    void createdTokenRoundTripsSubjectUidAndUserIdClaims() {
        String token = tokenProvider.createToken(admin());
        assertEquals(3, token.split("\\.").length, "the token must stay a signed JWS with three segments");

        Claims claims = tokenProvider.getClaims(token);
        assertEquals("admin", claims.getSubject());
        assertEquals(2L, ((Number) claims.get(TokenProvider.AUTHORITIES_UID_KEY)).longValue(),
                "the userId claim must survive issuance and parsing");

        String uid = tokenProvider.getId(token);
        assertNotEquals("", uid);
        assertEquals("online-token:admin:" + uid, tokenProvider.loginKey(token));
    }

    @Test
    void everyIssuedTokenCarriesAFreshUidClaim() {
        String first = tokenProvider.createToken(admin());
        String second = tokenProvider.createToken(admin());
        assertNotEquals(tokenProvider.getId(first), tokenProvider.getId(second),
                "each login must keep its own online key so concurrent sessions do not collide");
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = tokenProvider.createToken(admin());
        String tampered = token.substring(0, token.length() - 3) + "abc";
        assertThrows(JwtException.class, () -> tokenProvider.getClaims(tampered));
    }

    @Test
    void foreignSignedTokenIsRejected() {
        SecurityProperties other = new SecurityProperties();
        other.setHeader("Authorization");
        other.setTokenStartWith("Bearer ");
        other.setBase64Secret(Base64.getEncoder().encodeToString(
                "a-completely-different-test-secret-key-long-enough-for-hs512-9876543210fedcba"
                        .getBytes(StandardCharsets.UTF_8)));
        other.setOnlineKey("online-token:");
        other.setTokenValidityInSeconds(86400L);
        TokenProvider foreign = new TokenProvider(other, Mockito.mock(RedisUtils.class));
        foreign.afterPropertiesSet();

        String foreignToken = foreign.createToken(admin());
        assertThrows(JwtException.class, () -> tokenProvider.getClaims(foreignToken));
        assertTrue(foreignToken.length() > 0);
    }
}

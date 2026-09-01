/*
 *  Copyright 2026 SovNode (user password hash nonserialization closure); see NOTICE.
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
package me.zhengjie.modules.security.service.dto;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import me.zhengjie.modules.security.config.CaptchaConfig;
import me.zhengjie.config.properties.RsaProperties;
import me.zhengjie.modules.security.config.SecurityProperties;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.security.config.LoginProperties;
import me.zhengjie.modules.security.rest.AuthController;
import me.zhengjie.modules.security.security.TokenProvider;
import me.zhengjie.modules.security.service.OnlineUserService;
import me.zhengjie.modules.security.service.UserDetailsServiceImpl;
import me.zhengjie.modules.system.service.UserService;
import me.zhengjie.modules.system.service.dto.UserDto;
import me.zhengjie.utils.RedisUtils;
import me.zhengjie.utils.RsaUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户口令哈希的非序列化回归：GET /api/users、/auth/info 与登录响应都经
 * fastjson2 序列化 UserDto（含嵌套在 JwtUserDto 里的副本）。哈希字段被
 * @JSONField(serialize = false) 隐藏后，Redis 用户缓存里的副本不再携带哈希，
 * 登录比对必须在缓存副本缺哈希时回源数据库。本类用真实的 fastjson2 序列化
 * 与真实的 AuthController 登录流程锁定这两个行为，任何一处回退都会变红。
 */
@Tag("behavioral")
class UserPasswordNonSerializationTest {

    /** 形如 bcrypt 的占位哈希，只作泄漏标记，不对应任何真实口令。 */
    private static final String HASH_MARKER =
            "$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ012";

    private static String originalPrivateKey;

    @BeforeAll
    static void rememberStaticState() {
        originalPrivateKey = RsaProperties.privateKey;
    }

    @AfterAll
    static void restoreStaticState() {
        RsaProperties.privateKey = originalPrivateKey;
    }

    private static UserDto user(String password) {
        UserDto user = new UserDto();
        user.setId(2L);
        user.setUsername("alice");
        user.setEnabled(true);
        user.setPassword(password);
        return user;
    }

    private static JwtUserDto jwtUser(UserDto user) {
        return new JwtUserDto(user, List.of(1L, 2L),
                List.of(new AuthorityDto("user:list")));
    }

    @Test
    void directUserDtoSerializationOmitsThePasswordHash() {
        String json = JSON.toJSONString(user(HASH_MARKER));
        JSONObject parsed = JSON.parseObject(json);
        assertTrue(json.contains("alice"), "positive control: the dto body is serialized");
        assertFalse(json.contains(HASH_MARKER), "the bcrypt hash must not serialize");
        assertNull(parsed.getString("password"), "the password key must be absent");
    }

    @Test
    void nestedJwtUserDtoSerializationOmitsThePasswordHash() {
        String json = JSON.toJSONString(jwtUser(user(HASH_MARKER)));
        JSONObject parsed = JSON.parseObject(json);
        assertTrue(json.contains("alice"), "positive control: the nested user is serialized");
        assertTrue(json.contains("user:list"), "positive control: authorities are serialized");
        assertFalse(json.contains(HASH_MARKER), "the nested bcrypt hash must not serialize");
        assertNull(parsed.getJSONObject("user").getString("password"),
                "the nested user must not expose a password key");
    }

    @Test
    void redisCacheRoundTripCarriesNoHash() {
        JwtUserDto cached = JSON.parseObject(
                JSON.toJSONString(jwtUser(user(HASH_MARKER))), JwtUserDto.class);
        assertNotNull(cached);
        assertEquals("alice", cached.getUsername());
        assertNull(cached.getPassword(),
                "cache copies must round trip without the hash; login must source it elsewhere");
    }

    private static AuthController loginController(UserDetailsServiceImpl userDetailsService,
            UserService userService, BCryptPasswordEncoder encoder, TokenProvider tokenProvider) {
        RedisUtils redisUtils = mock(RedisUtils.class);
        when(redisUtils.get(anyString(), eq(String.class))).thenReturn("4321");
        LoginProperties loginProperties = new LoginProperties();
        loginProperties.setSingleLogin(false);
        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.setTokenStartWith("Bearer ");
        OnlineUserService onlineUserService = mock(OnlineUserService.class);
        return new AuthController(securityProperties, redisUtils, onlineUserService,
                tokenProvider, loginProperties, mock(CaptchaConfig.class), encoder,
                userDetailsService, userService);
    }

    private static AuthUserDto authUser(String rsaCipher) {
        AuthUserDto authUser = new AuthUserDto();
        authUser.setUsername("alice");
        authUser.setPassword(rsaCipher);
        authUser.setCode("4321");
        authUser.setUuid("captcha-uuid");
        return authUser;
    }

    private static JwtUserDto cacheHitCopy(UserDto stored) {
        // 模拟新格式缓存副本：经 fastjson2 序列化回读，哈希已丢失
        return JSON.parseObject(JSON.toJSONString(jwtUser(stored)), JwtUserDto.class);
    }

    @Test
    void loginMatchesAgainstTheDatabaseHashWhenTheCacheCopyHasNone() throws Exception {
        RsaUtils.RsaKeyPair pair = RsaUtils.generateKeyPair();
        RsaProperties.privateKey = pair.getPrivateKey();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        UserDto stored = user(encoder.encode("correct-horse-battery"));

        UserDetailsServiceImpl userDetailsService = mock(UserDetailsServiceImpl.class);
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(cacheHitCopy(stored));
        UserService userService = mock(UserService.class);
        when(userService.findByName("alice")).thenReturn(stored);
        TokenProvider tokenProvider = mock(TokenProvider.class);
        when(tokenProvider.createToken(any())).thenReturn("jwt-value");

        AuthController controller = loginController(userDetailsService, userService, encoder, tokenProvider);
        ResponseEntity<Object> response = controller.login(
                authUser(RsaUtils.encryptByPublicKey(pair.getPublicKey(), "correct-horse-battery")),
                new org.springframework.mock.web.MockHttpServletRequest());

        assertEquals(200, response.getStatusCode().value());
        verify(userService, times(1)).findByName("alice");
        assertTrue(response.getBody() instanceof java.util.Map, "login returns token plus user");
        String body = JSON.toJSONString(response.getBody());
        assertFalse(body.contains("correct-horse-battery"), "plaintext never serializes");
        assertFalse(body.contains(HASH_MARKER), "hash never serializes in the login response");
    }

    @Test
    void loginStillRejectsWrongPasswordsThroughTheDatabaseHash() throws Exception {
        RsaUtils.RsaKeyPair pair = RsaUtils.generateKeyPair();
        RsaProperties.privateKey = pair.getPrivateKey();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        UserDto stored = user(encoder.encode("correct-horse-battery"));

        UserDetailsServiceImpl userDetailsService = mock(UserDetailsServiceImpl.class);
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(cacheHitCopy(stored));
        UserService userService = mock(UserService.class);
        when(userService.findByName("alice")).thenReturn(stored);

        AuthController controller = loginController(userDetailsService, userService, encoder, mock(TokenProvider.class));
        assertThrows(BadRequestException.class, () -> controller.login(
                authUser(RsaUtils.encryptByPublicKey(pair.getPublicKey(), "wrong-password")),
                new org.springframework.mock.web.MockHttpServletRequest()));
    }

    @Test
    void loginSkipsTheDatabaseWhenTheCachedCopyStillCarriesTheHash() throws Exception {
        RsaUtils.RsaKeyPair pair = RsaUtils.generateKeyPair();
        RsaProperties.privateKey = pair.getPrivateKey();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        UserDto stored = user(encoder.encode("correct-horse-battery"));

        UserDetailsServiceImpl userDetailsService = mock(UserDetailsServiceImpl.class);
        // 旧格式缓存副本：仍带哈希
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(jwtUser(stored));
        UserService userService = mock(UserService.class);
        TokenProvider tokenProvider = mock(TokenProvider.class);
        when(tokenProvider.createToken(any())).thenReturn("jwt-value");

        AuthController controller = loginController(userDetailsService, userService, encoder, tokenProvider);
        ResponseEntity<Object> response = controller.login(
                authUser(RsaUtils.encryptByPublicKey(pair.getPublicKey(), "correct-horse-battery")),
                new org.springframework.mock.web.MockHttpServletRequest());

        assertEquals(200, response.getStatusCode().value());
        verify(userService, times(0)).findByName(anyString());
        assertNotEquals(HASH_MARKER, stored.getPassword());
    }
}

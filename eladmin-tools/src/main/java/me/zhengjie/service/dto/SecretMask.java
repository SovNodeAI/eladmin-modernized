/*
 *  Copyright 2026 SovNode (masked secret sentinel for redacted reads, SOV-1319); see NOTICE.
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
package me.zhengjie.service.dto;

/**
 * 脱敏读视图与更新入参共享的秘密掩码哨兵：读取侧用它占位已存秘密，
 * 更新侧把空白或回传的哨兵字面量一律解释为沿用已存秘密，
 * 只有非空且不等于哨兵的新值才触发轮换，哨兵永远不会被持久化。
 * @author SovNode
 * @date 2026-08-31
 */
public final class SecretMask {

    public static final String SENTINEL = "******";

    private SecretMask() {
    }

    /**
     * 更新入参是否表示“沿用已存秘密”：空白（null 或全空白）或掩码哨兵字面量。
     */
    public static boolean isRetained(String candidate) {
        return candidate == null || candidate.isBlank() || SENTINEL.equals(candidate);
    }

    /**
     * 读取侧占位：已存秘密存在时返回哨兵，否则返回 null 保持上游空表单行为。
     */
    public static String mask(String stored) {
        return (stored == null || stored.isBlank()) ? null : SENTINEL;
    }
}

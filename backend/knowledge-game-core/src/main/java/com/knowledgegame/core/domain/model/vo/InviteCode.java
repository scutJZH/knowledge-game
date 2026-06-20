package com.knowledgegame.core.domain.model.vo;

import java.security.SecureRandom;
import java.util.Objects;

/**
 * 邀请码值对象（8 位 Crockford Base32）
 * <p>
 * 字符集：0-9 A-H J-K M-N P-T V-Z（32 个字符，排除 I/L/O/U）
 * <p>
 * 不可变值对象，生成时使用 {@link SecureRandom}。
 */
public final class InviteCode {

    /**
     * Crockford Base32 字符集（32 个字符）
     */
    private static final String CROCKFORD_CHARS = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    private static final int CODE_LENGTH = 8;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final String value;

    private InviteCode(String value) {
        this.value = value;
    }

    /**
     * 随机生成 8 位 Crockford Base32 邀请码
     */
    public static InviteCode generate() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CROCKFORD_CHARS.charAt(SECURE_RANDOM.nextInt(CROCKFORD_CHARS.length())));
        }
        return new InviteCode(sb.toString());
    }

    /**
     * 从已有字符串构造（校验格式）
     *
     * @throws IllegalArgumentException 格式不合法（长度非 8 或含非法字符）
     */
    public static InviteCode of(String value) {
        if (value == null || value.length() != CODE_LENGTH) {
            throw new IllegalArgumentException(
                    "邀请码必须为 8 位字符，实际: " + (value == null ? "null" : value.length() + " 位")
            );
        }
        for (int i = 0; i < CODE_LENGTH; i++) {
            if (CROCKFORD_CHARS.indexOf(value.charAt(i)) == -1) {
                throw new IllegalArgumentException(
                        "邀请码包含非法字符 '" + value.charAt(i) + "'（位置 " + i + "）"
                );
            }
        }
        return new InviteCode(value);
    }

    /**
     * 判断输入字符串是否匹配此邀请码
     */
    public boolean matches(String input) {
        return this.value.equals(input);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InviteCode other)) return false;
        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "InviteCode{'" + value + "'}";
    }
}

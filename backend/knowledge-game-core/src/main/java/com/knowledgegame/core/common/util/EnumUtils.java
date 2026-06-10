package com.knowledgegame.core.common.util;

import com.knowledgegame.core.common.exception.BusinessException;

/**
 * 枚举工具类（安全的枚举转换，无效值抛 BusinessException）
 */
public final class EnumUtils {

    private EnumUtils() {
    }

    /**
     * 将字符串安全转换为枚举值，无效值抛出 BusinessException
     *
     * @param enumClass 枚举类
     * @param name      枚举名称字符串
     * @param <E>       枚举类型
     * @return 对应的枚举值
     */
    public static <E extends Enum<E>> E valueOf(Class<E> enumClass, String name) {
        try {
            return java.lang.Enum.valueOf(enumClass, name);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("无效的" + enumClass.getSimpleName() + "值: " + name);
        }
    }

    /**
     * 将字符串安全转换为枚举值，字符串为 null 时返回 null
     *
     * @param enumClass 枚举类
     * @param name      枚举名称字符串，可为 null
     * @param <E>       枚举类型
     * @return 对应的枚举值，name 为 null 时返回 null
     */
    public static <E extends Enum<E>> E valueOfNullable(Class<E> enumClass, String name) {
        if (name == null) {
            return null;
        }
        return valueOf(enumClass, name);
    }
}

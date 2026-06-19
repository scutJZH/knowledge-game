package com.knowledgegame.core.common.result;

import lombok.Getter;

/**
 * 统一返回码枚举
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "success"),
    FAIL(400, "操作失败"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),
    BAD_CREDENTIALS(400, "用户名或密码错误"),
    DUPLICATE_USERNAME(400, "用户名已存在"),
    USER_NOT_FOUND(400, "用户不存在"),
    ACCESS_DENIED(403, "无管理员权限"),
    INVALID_TOKEN(400, "Token 无效或已过期"),
    FILE_NOT_FOUND(400, "文件不存在"),
    FILE_BIZ_TYPE_MISMATCH(400, "文件业务类型不匹配"),
    FILE_OWNER_MISMATCH(403, "无权使用该文件"),
    PARAM_ERROR(400, "参数错误"),
    NOT_IMPLEMENTED(501, "功能未实现");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}

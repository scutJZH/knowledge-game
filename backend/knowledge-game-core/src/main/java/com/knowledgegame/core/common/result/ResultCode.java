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
    NOT_IMPLEMENTED(501, "功能未实现"),
    GROUP_NOT_FOUND(404, "群组不存在"),
    GROUP_JOIN_POLICY_MISMATCH(400, "该群组需要邀请码加入"),
    ALREADY_GROUP_MEMBER(400, "已是群组成员"),
    INVITE_CODE_INVALID(400, "邀请码无效"),
    NOT_GROUP_MEMBER(403, "非群组成员"),
    OWNER_CANNOT_LEAVE(400, "群主不能退出，请先转让群组"),
    NOT_GROUP_OWNER(403, "仅群主可操作"),
    INVITE_CODE_GENERATION_FAILED(400, "邀请码生成失败，请重试"),
    CANNOT_CHANGE_OWNER_ROLE(400, "不能通过此接口修改群主角色，请使用转让功能"),
    NOT_GROUP_ADMIN(403, "仅群主或管理员可操作"),
    IP_SERIES_NOT_FOUND(400, "IP系列不存在"),
    IP_SERIES_NOT_ACTIVE(400, "IP系列未启用");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}

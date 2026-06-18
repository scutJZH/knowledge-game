package com.knowledgegame.core.domain.spec;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.vo.SortField;

import java.util.Set;

/**
 * 排序字段白名单校验器（领域规则）
 * <p>
 * 所有管理端列表查询接口的 sort 参数必须经此校验。非法字段抛 BusinessException(400)，
 * 消息包含允许字段列表，便于前端定位配置错误。
 * <p>
 * 此类为项目通用规范的一部分（REQ-86 确立），未来所有列表查询接口必须遵循。
 */
public final class SortFieldSpec {

    private SortFieldSpec() {
    }

    /**
     * 校验 sort 字段是否在白名单内
     *
     * @param sortField     客户端传入的排序（可能为 null，表示用默认）
     * @param allowedFields 该接口允许的字段白名单（PO 字段名集合）
     * @return 校验通过的 SortField（入参为 null 时返回 null，由调用方决定默认值）
     * @throws BusinessException 字段不在白名单时抛 400，消息含允许字段列表
     */
    public static SortField validate(SortField sortField, Set<String> allowedFields) {
        if (sortField == null) {
            return null;
        }
        if (!allowedFields.contains(sortField.getField())) {
            throw new BusinessException(400,
                    "不支持的排序字段: " + sortField.getField()
                            + "，允许的字段: " + allowedFields);
        }
        return sortField;
    }
}

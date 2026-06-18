package com.knowledgegame.core.infrastructure.adapter.support;

import com.knowledgegame.core.domain.model.vo.SortField;
import org.springframework.data.domain.Sort;

/**
 * 排序字段领域类型 ↔ Spring Data Sort 转换工具
 * <p>
 * 供 4 个 RepositoryAdapter（Question/IpSeries/CardTemplate/KnowledgeCategory）共享，
 * 避免每个 Adapter 各自复制相同的 toSpringSort 私有方法。
 * <p>
 * 放在 infrastructure/adapter/support/ 子包：
 * <ul>
 *   <li>依赖 Spring Data Sort → 必须在 infrastructure 层（domain 层零框架依赖）</li>
 *   <li>adapter 子包内部辅助工具 → 不引入新顶层约定（与 db/converter/ MapStruct 同性质）</li>
 * </ul>
 * <p>
 * 此类为项目通用规范的一部分（REQ-86 确立）。
 */
public final class SortFields {

    private SortFields() {
    }

    /**
     * 将领域 SortField 转换为 Spring Data Sort
     *
     * @param sortField 领域排序值对象（已通过 SortFieldSpec.validate 白名单校验，方向非 null）
     * @return Spring Data Sort 对象
     */
    public static Sort toSpringSort(SortField sortField) {
        Sort.Direction direction = sortField.getDirection() == SortField.Direction.ASC
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, sortField.getField());
    }
}

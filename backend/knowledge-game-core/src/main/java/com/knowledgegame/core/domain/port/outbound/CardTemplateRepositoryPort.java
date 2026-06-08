package com.knowledgegame.core.domain.port.outbound;

import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import com.knowledgegame.core.domain.model.entity.CardTemplate;
import com.knowledgegame.core.domain.model.vo.PageResult;

import java.util.Optional;

/**
 * 卡牌模板仓储出端口（领域层定义，基础设施层实现）
 */
public interface CardTemplateRepositoryPort {

    /**
     * 保存卡牌模板
     */
    CardTemplate save(CardTemplate cardTemplate);

    /**
     * 根据 ID 查询
     */
    Optional<CardTemplate> findById(Long id);

    /**
     * 根据 code 查询
     */
    Optional<CardTemplate> findByCode(String code);

    /**
     * 分页查询（支持名称模糊 + IP 系列 + 稀有度 + 状态筛选）
     */
    PageResult<CardTemplate> findByConditions(String name, Long ipSeriesId,
                                              CardRarity rarity, CardTemplateStatus status,
                                              int pageNumber, int pageSize);

    /**
     * 根据 ID 判断是否存在
     */
    boolean existsById(Long id);
}

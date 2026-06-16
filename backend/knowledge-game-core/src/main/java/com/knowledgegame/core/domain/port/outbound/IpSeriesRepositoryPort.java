package com.knowledgegame.core.domain.port.outbound;

import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.entity.IpSeries;
import com.knowledgegame.core.domain.model.vo.PageResult;

import java.util.List;
import java.util.Optional;

/**
 * IP 系列仓储出端口（领域层定义，基础设施层实现）
 */
public interface IpSeriesRepositoryPort {

    /**
     * 保存 IP 系列
     */
    IpSeries save(IpSeries ipSeries);

    /**
     * 根据 ID 查询
     */
    Optional<IpSeries> findById(Long id);

    /**
     * 根据 code 查询
     */
    Optional<IpSeries> findByCode(String code);

    /**
     * 根据名称查询
     */
    Optional<IpSeries> findByName(String name);

    /**
     * 分页查询（支持名称模糊搜索 + 状态筛选）
     */
    PageResult<IpSeries> findByConditions(String name, IpSeriesStatus status, int pageNumber, int pageSize);

    /**
     * 根据 ID 批量查询（用于批量校验时一次性加载）
     */
    List<IpSeries> findAllByIdIn(List<Long> ids);

    /**
     * 根据 ID 判断是否存在
     */
    boolean existsById(Long id);
}

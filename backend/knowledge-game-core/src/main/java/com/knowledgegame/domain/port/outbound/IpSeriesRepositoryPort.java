package com.knowledgegame.domain.port.outbound;

import com.knowledgegame.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.domain.model.entity.IpSeries;
import com.knowledgegame.domain.model.vo.PageResult;

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
     * 根据 ID 判断是否存在
     */
    boolean existsById(Long id);
}

package com.knowledgegame.infrastructure.db.repository;

import com.knowledgegame.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.infrastructure.db.entity.IpSeriesPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * IP 系列 Spring Data JPA Repository
 */
public interface IpSeriesJpaRepository extends JpaRepository<IpSeriesPO, Long>,
        JpaSpecificationExecutor<IpSeriesPO> {

    /**
     * 根据 code 查询
     */
    Optional<IpSeriesPO> findByCode(String code);

    /**
     * 根据名称查询
     */
    Optional<IpSeriesPO> findByName(String name);
}

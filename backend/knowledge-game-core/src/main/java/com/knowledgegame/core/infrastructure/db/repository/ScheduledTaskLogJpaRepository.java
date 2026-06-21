package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.infrastructure.db.entity.ScheduledTaskLogPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 定时任务执行日志 Spring Data JPA Repository
 */
public interface ScheduledTaskLogJpaRepository extends JpaRepository<ScheduledTaskLogPO, Long>,
        JpaSpecificationExecutor<ScheduledTaskLogPO> {
}

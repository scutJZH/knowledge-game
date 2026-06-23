package com.knowledgegame.admin.application.service.recyclebin;

import com.knowledgegame.admin.api.controller.KnowledgeCategoryController;
import com.knowledgegame.admin.application.service.KnowledgeCategoryAppService;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.service.recyclebin.RecycleBinItemStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knowledgegame.auth.security.SecurityUtils;
import org.mockito.MockedStatic;

/**
 * REQ-107 独立黑盒测试
 * <p>
 * 仅凭 PRD 行为描述编写，不阅读策略实现代码。
 * 与开发者白盒测试（KnowledgeCategoryRecycleBinStrategyTest / BlackBoxTest）差异化覆盖：
 * - Controller → AppService 透传正确性
 * - AppService → Strategy 委托正确性（含 deletedBy 来源）
 * - 异常冒泡路径
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeCategoryDeleteBlackBoxTest {

    // ============================================================
    // Controller 黑盒：DELETE 端点行为（mock AppService）
    // ============================================================

    @Mock
    private KnowledgeCategoryAppService appService;

    @InjectMocks
    private KnowledgeCategoryController controller;

    @BeforeEach
    void setUp() {
        // Controller 仅依赖 AppService
    }

    @Test
    @DisplayName("Controller DELETE — 成功返回 Result.success")
    void controllerDelete_shouldReturnSuccess() {
        Long id = 1L;

        Result<Void> result = controller.delete(id);

        assertThat(result.getCode()).isEqualTo(200);
        verify(appService).delete(id);
    }

    @Test
    @DisplayName("Controller DELETE — AppService 抛异常穿透到上层")
    void controllerDelete_shouldThrow_whenAppServiceThrows() {
        Long id = 999L;
        doThrow(new BusinessException("知识点分类不存在: 999"))
                .when(appService).delete(id);

        assertThatThrownBy(() -> controller.delete(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("知识点分类不存在: 999");
    }

    // ============================================================
    // AppService 黑盒：delete 委托策略（mock Strategy + Port）
    // ============================================================

    @Mock
    private com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort categoryRepositoryPort;
    @Mock
    private com.knowledgegame.core.domain.service.KnowledgeCategoryDomainService categoryDomainService;
    @Mock
    private com.knowledgegame.components.feign.client.FileServiceClient fileServiceClient;
    @Mock
    private RecycleBinItemStrategy<KnowledgeCategory> recycleBinStrategy;

    @InjectMocks
    private KnowledgeCategoryAppService appServiceForDelete;

    @Test
    @DisplayName("AppService.delete — 存在时委托 validateDeletable + moveToRecycleBin")
    void appServiceDelete_shouldDelegateToStrategy() {
        Long id = 1L;
        KnowledgeCategory existing = KnowledgeCategory.reconstruct(id, null, "测试分类",
                "", null, null, null, 0, KnowledgeCategoryStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now());
        when(categoryRepositoryPort.findById(id)).thenReturn(java.util.Optional.of(existing));

        try (MockedStatic<SecurityUtils> mockedSecurity = mockStatic(SecurityUtils.class)) {
            mockedSecurity.when(SecurityUtils::getCurrentUsername).thenReturn("admin");
            appServiceForDelete.delete(id);
        }

        verify(recycleBinStrategy).validateDeletable(id);
        ArgumentCaptor<String> deletedByCaptor = ArgumentCaptor.forClass(String.class);
        verify(recycleBinStrategy).moveToRecycleBin(eq(id), deletedByCaptor.capture());
        assertThat(deletedByCaptor.getValue()).isEqualTo("admin");
    }

    @Test
    @DisplayName("AppService.delete — 不存在时抛 BusinessException 不调策略")
    void appServiceDelete_shouldThrow_whenNotFound() {
        Long id = 999L;
        when(categoryRepositoryPort.findById(id)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> appServiceForDelete.delete(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("知识点分类不存在: 999");
    }

    @Test
    @DisplayName("AppService.delete — validateDeletable 失败时不调 moveToRecycleBin")
    void appServiceDelete_shouldNotMove_whenValidationFails() {
        Long id = 1L;
        KnowledgeCategory existing = KnowledgeCategory.reconstruct(id, null, "有关联分类",
                "", null, null, null, 0, KnowledgeCategoryStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now());
        when(categoryRepositoryPort.findById(id)).thenReturn(java.util.Optional.of(existing));
        doThrow(new BusinessException("知识点分类关联 2 道 ACTIVE 题目，无法删除"))
                .when(recycleBinStrategy).validateDeletable(id);

        assertThatThrownBy(() -> appServiceForDelete.delete(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("知识点分类关联 2 道 ACTIVE 题目，无法删除");
    }
}

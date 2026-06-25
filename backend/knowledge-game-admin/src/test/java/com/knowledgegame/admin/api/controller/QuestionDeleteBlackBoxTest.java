package com.knowledgegame.admin.api.controller;

import com.knowledgegame.admin.application.service.QuestionAppService;
import com.knowledgegame.auth.security.SecurityUtils;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.core.domain.model.entity.Question;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.QuestionRepository;
import com.knowledgegame.core.domain.service.QuestionDomainService;
import com.knowledgegame.core.domain.service.recyclebin.RecycleBinItemStrategy;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Black-box test for Question DELETE (REQ-106 recycle bin integration).
 * <p>
 * Uses pure Mockito (no Spring container) to avoid Nacos startup failures.
 * Tests the controller→appService contract and the appService→strategy flow.
 * Differs from the developer's {@code @DataJpaTest} whitebox tests which test
 * strategy internals against a real database.
 */
@ExtendWith(MockitoExtension.class)
class QuestionDeleteBlackBoxTest {

    // ==================== Controller-level tests ====================

    @Nested
    class ControllerDelete {

        @Mock
        QuestionAppService appService;

        @InjectMocks
        QuestionController controller;

        /**
         * Test 1: Controller DELETE endpoint returns Result.success() when
         * AppService.delete() succeeds (implicit 200 code).
         */
        @Test
        void delete_shouldReturnSuccess_whenAppServiceSucceeds() {
            Result<Void> result = controller.delete(1L);

            assertThat(result.getCode()).isEqualTo(200);
            verify(appService).delete(1L);
        }

        /**
         * Test 2: Controller DELETE calls appService.delete(id) exactly once.
         * Implicit verify() default is times(1), and verifyNoMoreInteractions
         * confirms no other service method was invoked.
         */
        @Test
        void delete_shouldCallAppServiceDeleteExactlyOnce() {
            controller.delete(42L);

            verify(appService).delete(42L);
            verifyNoMoreInteractions(appService);
        }

        /**
         * Test 3: Controller DELETE propagates BusinessException when
         * AppService.delete() throws. The exception passes through the
         * controller unchanged — GlobalExceptionHandler handles it separately.
         */
        @Test
        void delete_shouldPropagateBusinessException() {
            BusinessException expected = new BusinessException("题目不存在: 99");
            doThrow(expected).when(appService).delete(99L);

            BusinessException thrown = assertThrows(BusinessException.class,
                    () -> controller.delete(99L));

            assertThat(thrown).isSameAs(expected);
            verify(appService).delete(99L);
        }
    }

    // ==================== AppService-level tests ====================

    @Nested
    class AppServiceDeleteFlow {

        @Mock
        QuestionRepository questionRepository;

        @Mock
        QuestionDomainService questionDomainService;

        @Mock
        KnowledgeCategoryRepositoryPort categoryRepositoryPort;

        @Mock
        RecycleBinItemStrategy<Question> recycleBinStrategy;

        @InjectMocks
        QuestionAppService appService;

        /**
         * Test 4: Verify the delete flow calls strategy.validateDeletable(id)
         * first, then strategy.moveToRecycleBin(eq(id), any()) with the
         * username obtained from SecurityUtils.getCurrentUsername().
         */
        @Test
        void delete_shouldCallValidateDeletableThenMoveToRecycleBin() {
            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUsername).thenReturn("admin");

                appService.delete(1L);

                InOrder inOrder = inOrder(recycleBinStrategy);
                inOrder.verify(recycleBinStrategy).validateDeletable(1L);
                inOrder.verify(recycleBinStrategy).moveToRecycleBin(eq(1L), eq("admin"));

                securityUtils.verify(SecurityUtils::getCurrentUsername);
            }
        }

        /**
         * Test 5: When SecurityUtils.getCurrentUsername() returns null,
         * the delete flow must not NPE. The null username is passed through
         * to moveToRecycleBin (the strategy decides how to handle it).
         * Also verifies that SecurityUtils.getCurrentUsername() is called.
         */
        @Test
        void delete_shouldNotNpe_whenSecurityUtilsReturnsNull() {
            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUsername).thenReturn(null);

                assertDoesNotThrow(() -> appService.delete(1L));

                verify(recycleBinStrategy).validateDeletable(1L);
                verify(recycleBinStrategy).moveToRecycleBin(eq(1L), eq(null));
                securityUtils.verify(SecurityUtils::getCurrentUsername);
            }
        }
    }
}

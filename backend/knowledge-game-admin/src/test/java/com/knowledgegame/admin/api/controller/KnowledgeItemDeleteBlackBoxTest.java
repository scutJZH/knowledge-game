package com.knowledgegame.admin.api.controller;

import com.knowledgegame.admin.application.service.KnowledgeItemAppService;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.Result;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Black-box test for KnowledgeItem Controller DELETE (REQ-108 recycle bin integration).
 * <p>
 * Uses pure Mockito (no Spring container) to avoid Nacos startup failures.
 * Focuses on the Controller→AppService boundary only.
 * AppService→Strategy flow is covered by KnowledgeItemAppServiceTest (white-box).
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeItemDeleteBlackBoxTest {

    @Nested
    class ControllerDelete {

        @Mock
        KnowledgeItemAppService appService;

        @InjectMocks
        KnowledgeItemController controller;

        @Test
        void delete_shouldReturnSuccess_whenAppServiceSucceeds() {
            Result<Void> result = controller.delete(1L);

            assertThat(result.getCode()).isEqualTo(200);
            verify(appService).delete(1L);
        }

        @Test
        void delete_shouldCallAppServiceDeleteExactlyOnce() {
            controller.delete(42L);

            verify(appService).delete(42L);
            verifyNoMoreInteractions(appService);
        }

        @Test
        void delete_shouldPropagateBusinessException() {
            BusinessException expected = new BusinessException("知识条目不存在: 99");
            doThrow(expected).when(appService).delete(99L);

            BusinessException thrown = assertThrows(BusinessException.class,
                    () -> controller.delete(99L));

            assertThat(thrown).isSameAs(expected);
            verify(appService).delete(99L);
        }
    }
}

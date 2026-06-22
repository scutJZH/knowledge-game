package com.knowledgegame.app.api.controller;

import com.knowledgegame.app.api.dto.StudyGroupListResponse;
import com.knowledgegame.app.application.service.StudyGroupAppService;
import com.knowledgegame.app.config.JacksonConfig;
import com.knowledgegame.components.exception.handler.GlobalExceptionHandler;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 黑盒测试：仅凭 PRD 行为描述编写，不参考 Controller/AppService 实现代码。
 * 侧重异常路径（mock AppService 抛异常 → GlobalExceptionHandler 响应格式）。
 */
@WebMvcTest(controllers = StudyGroupController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
class StudyGroupControllerBlackBoxTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudyGroupAppService appService;

    @Test
    @DisplayName("GET /api/study-groups 返回列表 → 验证 JSON 结构含 myRole + memberCount + 毫秒戳 + nullable 字段")
    void listMyGroups_returnsCorrectJsonStructure() throws Exception {
        StudyGroupListResponse item = new StudyGroupListResponse();
        item.setId(1L);
        item.setName("测试群组");
        item.setDescription("描述");
        item.setAvatarFileId(10L);
        item.setAvatarUrl("https://example.com/avatar.png");
        item.setOwnerId(100L);
        item.setJoinPolicy("OPEN");
        item.setMyRole("OWNER");
        item.setMemberCount(12);
        item.setCreatedAt(1718800000000L);
        item.setUpdatedAt(1719900000000L);
        when(appService.listMyGroups()).thenReturn(List.of(item));

        mockMvc.perform(get("/api/study-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].myRole").value("OWNER"))
                .andExpect(jsonPath("$.data[0].memberCount").value(12))
                .andExpect(jsonPath("$.data[0].updatedAt").isNumber())
                .andExpect(jsonPath("$.data[0].avatarFileId").value(10))
                .andExpect(jsonPath("$.data[0].avatarUrl").value("https://example.com/avatar.png"));
    }

    @Test
    @DisplayName("GET /api/study-groups 空列表 → data 是空数组非 null")
    void listMyGroups_empty_returnsEmptyArray() throws Exception {
        when(appService.listMyGroups()).thenReturn(List.of());

        mockMvc.perform(get("/api/study-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("AppService 抛 BusinessException → GlobalExceptionHandler 包装为 Result(code=异常码, HTTP 200)")
    void listMyGroups_appServiceThrows_returnsWrappedError() throws Exception {
        when(appService.listMyGroups()).thenThrow(new BusinessException(ResultCode.INTERNAL_ERROR));

        mockMvc.perform(get("/api/study-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").isString());
    }
}

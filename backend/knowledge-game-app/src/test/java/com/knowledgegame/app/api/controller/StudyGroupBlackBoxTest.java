package com.knowledgegame.app.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegame.app.api.dto.CreateStudyGroupRequest;
import com.knowledgegame.app.api.dto.StudyGroupResponse;
import com.knowledgegame.app.application.service.StudyGroupAppService;
import com.knowledgegame.app.config.JacksonConfig;
import com.knowledgegame.components.exception.handler.GlobalExceptionHandler;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * StudyGroupController 黑盒集成测试。
 * 基于 API 契约编写，不依赖实现细节。
 * 使用 {@code @SpringBootTest} 加载完整上下文，通过 {@code @MockitoBean} 隔离 AppService。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
@ActiveProfiles("test")
@DisplayName("StudyGroupController 黑盒集成测试")
class StudyGroupBlackBoxTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StudyGroupAppService appService;

    // ============================================================
    // 创建成功
    // ============================================================

    @Test
    @DisplayName("POST /api/study-groups — 创建成功返回 200 + 正确字段")
    void create_shouldReturn200WithCorrectFields() throws Exception {
        StudyGroupResponse response = buildResponse(1L, "测试群组", "描述文本", 10L,
                "https://example.com/avatar.png", 100L, 1718800000000L, 1718800000000L);
        when(appService.create(any())).thenReturn(response);

        CreateStudyGroupRequest request = buildRequest("测试群组", "描述文本", 10L);

        mockMvc.perform(post("/api/study-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("测试群组"))
                .andExpect(jsonPath("$.data.description").value("描述文本"))
                .andExpect(jsonPath("$.data.avatarFileId").value(10))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://example.com/avatar.png"))
                .andExpect(jsonPath("$.data.ownerId").value(100))
                .andExpect(jsonPath("$.data.createdAt").value(1718800000000L))
                .andExpect(jsonPath("$.data.updatedAt").value(1718800000000L));
    }

    @Test
    @DisplayName("POST /api/study-groups — 无头像创建成功（avatarFileId/avatarUrl 为 null）")
    void create_shouldReturn200_whenNoAvatar() throws Exception {
        StudyGroupResponse response = buildResponse(2L, "无头像群组", null, null,
                null, 100L, 1718800000000L, 1718800000000L);
        when(appService.create(any())).thenReturn(response);

        CreateStudyGroupRequest request = buildRequest("无头像群组", null, null);

        mockMvc.perform(post("/api/study-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("无头像群组"))
                .andExpect(jsonPath("$.data.avatarFileId").isEmpty())
                .andExpect(jsonPath("$.data.avatarUrl").isEmpty())
                .andExpect(jsonPath("$.data.ownerId").value(100));
    }

    // ============================================================
    // 校验失败
    // ============================================================

    @Test
    @DisplayName("POST /api/study-groups — name 为空（空字符串）返回 400")
    void create_shouldReturn400_whenNameIsBlank() throws Exception {
        CreateStudyGroupRequest request = buildRequest("", null, null);

        mockMvc.perform(post("/api/study-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("name")));
    }

    @Test
    @DisplayName("POST /api/study-groups — name 超长（51 字符）返回 400")
    void create_shouldReturn400_whenNameTooLong() throws Exception {
        CreateStudyGroupRequest request = buildRequest("a".repeat(51), null, null);

        mockMvc.perform(post("/api/study-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("name")));
    }

    @Test
    @DisplayName("POST /api/study-groups — description 超长（501 字符）返回 400")
    void create_shouldReturn400_whenDescriptionTooLong() throws Exception {
        CreateStudyGroupRequest request = buildRequest("合法名称", "a".repeat(501), null);

        mockMvc.perform(post("/api/study-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("description")));
    }

    // ============================================================
    // BusinessException 透传
    // ============================================================

    @Test
    @DisplayName("POST /api/study-groups — BusinessException(FILE_NOT_FOUND) 透传为 code=400 + 正确消息")
    void create_shouldReturn400_whenFileNotFound() throws Exception {
        when(appService.create(any()))
                .thenThrow(new BusinessException(ResultCode.FILE_NOT_FOUND));

        CreateStudyGroupRequest request = buildRequest("群组", null, 999L);

        mockMvc.perform(post("/api/study-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FILE_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(ResultCode.FILE_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("POST /api/study-groups — BusinessException(FILE_BIZ_TYPE_MISMATCH) 透传正确")
    void create_shouldReturn400_whenBizTypeMismatch() throws Exception {
        when(appService.create(any()))
                .thenThrow(new BusinessException(ResultCode.FILE_BIZ_TYPE_MISMATCH));

        CreateStudyGroupRequest request = buildRequest("群组", null, 10L);

        mockMvc.perform(post("/api/study-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FILE_BIZ_TYPE_MISMATCH.getCode()))
                .andExpect(jsonPath("$.message").value(ResultCode.FILE_BIZ_TYPE_MISMATCH.getMessage()))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("POST /api/study-groups — BusinessException(FILE_OWNER_MISMATCH) 透传正确")
    void create_shouldReturn403_whenOwnerMismatch() throws Exception {
        when(appService.create(any()))
                .thenThrow(new BusinessException(ResultCode.FILE_OWNER_MISMATCH));

        CreateStudyGroupRequest request = buildRequest("群组", null, 10L);

        mockMvc.perform(post("/api/study-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FILE_OWNER_MISMATCH.getCode()))
                .andExpect(jsonPath("$.message").value(ResultCode.FILE_OWNER_MISMATCH.getMessage()))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ============================================================
    // 同名群组无唯一约束
    // ============================================================

    @Test
    @DisplayName("POST /api/study-groups — 同一用户创建两个同名群组均成功")
    void create_shouldAllowDuplicateNameForSameUser() throws Exception {
        StudyGroupResponse response1 = buildResponse(1L, "同名群组", null, null,
                null, 100L, 1718800000000L, 1718800000000L);
        StudyGroupResponse response2 = buildResponse(2L, "同名群组", null, null,
                null, 100L, 1718800000001L, 1718800000001L);
        when(appService.create(any())).thenReturn(response1, response2);

        CreateStudyGroupRequest request = buildRequest("同名群组", null, null);

        // 第一次创建
        mockMvc.perform(post("/api/study-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("同名群组"));

        // 第二次创建（同名）
        mockMvc.perform(post("/api/study-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.name").value("同名群组"));
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private CreateStudyGroupRequest buildRequest(String name, String description, Long avatarFileId) {
        CreateStudyGroupRequest request = new CreateStudyGroupRequest();
        request.setName(name);
        request.setDescription(description);
        request.setAvatarFileId(avatarFileId);
        return request;
    }

    private StudyGroupResponse buildResponse(Long id, String name, String description,
                                             Long avatarFileId, String avatarUrl,
                                             Long ownerId, Long createdAt, Long updatedAt) {
        StudyGroupResponse response = new StudyGroupResponse();
        response.setId(id);
        response.setName(name);
        response.setDescription(description);
        response.setAvatarFileId(avatarFileId);
        response.setAvatarUrl(avatarUrl);
        response.setOwnerId(ownerId);
        response.setCreatedAt(createdAt);
        response.setUpdatedAt(updatedAt);
        return response;
    }
}

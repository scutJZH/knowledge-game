package com.knowledgegame.app.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegame.app.api.dto.CreateStudyGroupRequest;
import com.knowledgegame.app.api.dto.StudyGroupResponse;
import com.knowledgegame.app.application.service.StudyGroupAppService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudyGroupController.class)
@AutoConfigureMockMvc(addFilters = false)
class StudyGroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudyGroupAppService appService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("创建成功应返回 200 + 正确字段")
    void create_shouldReturn200() throws Exception {
        StudyGroupResponse response = new StudyGroupResponse();
        response.setId(1L);
        response.setName("测试群组");
        response.setDescription("描述");
        response.setAvatarFileId(10L);
        response.setAvatarUrl("https://example.com/avatar.png");
        response.setOwnerId(100L);
        response.setCreatedAt(1718800000000L);
        response.setUpdatedAt(1718800000000L);
        when(appService.create(any())).thenReturn(response);

        CreateStudyGroupRequest request = new CreateStudyGroupRequest();
        request.setName("测试群组");
        request.setDescription("描述");
        request.setAvatarFileId(10L);

        mockMvc.perform(post("/api/study-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("测试群组"))
                .andExpect(jsonPath("$.data.avatarFileId").value(10))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://example.com/avatar.png"))
                .andExpect(jsonPath("$.data.ownerId").value(100))
                .andExpect(jsonPath("$.data.createdAt").value(1718800000000L))
                .andExpect(jsonPath("$.data.updatedAt").value(1718800000000L));
    }
}

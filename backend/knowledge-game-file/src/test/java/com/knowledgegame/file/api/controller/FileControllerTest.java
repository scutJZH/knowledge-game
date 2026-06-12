package com.knowledgegame.file.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegame.file.api.dto.FileInfoResponse;
import com.knowledgegame.file.api.dto.FileUploadResponse;
import com.knowledgegame.file.application.FileAppService;
import com.knowledgegame.file.common.config.FileProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文件 Controller 测试
 */
@WebMvcTest(FileController.class)
@Import(FileProperties.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileAppService fileAppService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("生成上传凭证应返回 token")
    void shouldGenerateCredential() throws Exception {
        when(fileAppService.generateCredential(1L, 1, "ip-series")).thenReturn("test-token");

        mockMvc.perform(post("/api/file/internal/credential")
                        .param("userId", "1")
                        .param("basePath", "ip-series"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("test-token"));
    }

    @Test
    @DisplayName("上传文件应返回 fileId 和 url")
    void shouldUploadFile() throws Exception {
        FileUploadResponse response = FileUploadResponse.builder()
                .fileId(1L).url("/static/ip-series/20260612/uuid.png").build();
        when(fileAppService.uploadFile(anyLong(), anyString(), any()))
                .thenReturn(response);

        MockMultipartFile file = new MockMultipartFile("file", "test.png",
                "image/png", "hello".getBytes());

        mockMvc.perform(multipart("/api/file/upload")
                        .file(file)
                        .header("X-Upload-Token", "test-token")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileId").value(1))
                .andExpect(jsonPath("$.data.url").value("/static/ip-series/20260612/uuid.png"));
    }

    @Test
    @DisplayName("删除文件应返回成功")
    void shouldDeleteFile() throws Exception {
        mockMvc.perform(delete("/api/file/internal/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("查询文件信息应返回文件详情")
    void shouldGetFileInfo() throws Exception {
        FileInfoResponse response = FileInfoResponse.builder()
                .fileId(1L)
                .url("/static/ip-series/20260612/uuid.png")
                .originalName("test.png")
                .contentType("image/png")
                .fileSize(100L)
                .basePath("ip-series")
                .uploaderId(1L)
                .createdAt(LocalDateTime.now())
                .build();
        when(fileAppService.getFileInfo(1L)).thenReturn(response);

        mockMvc.perform(get("/api/file/internal/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileId").value(1))
                .andExpect(jsonPath("$.data.originalName").value("test.png"));
    }

    @Test
    @DisplayName("批量查询应返回文件列表")
    void shouldBatchGetUrls() throws Exception {
        FileInfoResponse response = FileInfoResponse.builder()
                .fileId(1L).url("/static/test.png").build();
        when(fileAppService.batchGetUrls(List.of(1L, 2L))).thenReturn(List.of(response));

        mockMvc.perform(post("/api/file/internal/batch-urls")
                        .contentType("application/json")
                        .content("{\"fileIds\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}

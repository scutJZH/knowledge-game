package com.knowledgegame.file.application;

import com.knowledgegame.components.feign.dto.FileInfoResponse;
import com.knowledgegame.file.api.dto.FileUploadResponse;
import com.knowledgegame.file.common.config.FileProperties;
import com.knowledgegame.file.domain.model.FileInfo;
import com.knowledgegame.file.domain.model.StoredFile;
import com.knowledgegame.file.domain.port.outbound.FileInfoRepository;
import com.knowledgegame.file.domain.port.outbound.FileStorageProvider;
import com.knowledgegame.file.domain.service.UploadCredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

/**
 * 文件应用服务测试
 */
@ExtendWith(MockitoExtension.class)
class FileAppServiceTest {

    @Mock
    private FileStorageProvider storageProvider;

    @Mock
    private FileInfoRepository fileInfoRepository;

    private UploadCredentialService credentialService;

    private FileProperties properties;

    private FileAppService fileAppService;

    @BeforeEach
    void setUp() {
        credentialService = new UploadCredentialService(5);
        properties = new FileProperties();
        fileAppService = new FileAppService(storageProvider, fileInfoRepository, credentialService, properties);
    }

    @Nested
    @DisplayName("上传文件")
    class UploadTests {

        @Test
        @DisplayName("成功上传文件")
        void shouldUploadFile() {
            String token = credentialService.generateCredential(1L, 1, "ip-series", null);

            StoredFile storedFile = new StoredFile("uuid.png", "ip-series/20260612/uuid.png",
                    "/static/ip-series/20260612/uuid.png", "image/png", 100);
            when(storageProvider.store(anyString(), anyString(), any(), any(long.class), anyString()))
                    .thenReturn(storedFile);

            FileInfo savedFileInfo = FileInfo.reconstruct(1L, "test.png", "uuid.png",
                    "ip-series/20260612/uuid.png", "/static/ip-series/20260612/uuid.png",
                    "image/png", 100, "ip-series", 1L, LocalDateTime.now(), false, null);
            when(fileInfoRepository.save(any(FileInfo.class))).thenReturn(savedFileInfo);

            MockMultipartFile file = new MockMultipartFile("file", "test.png",
                    "image/png", "hello".getBytes());

            FileUploadResponse response = fileAppService.uploadFile(1L, token, file);

            assertNotNull(response);
            assertEquals(1L, response.getFileId());
            // 凭证应已消费
            assertThrows(Exception.class, () -> fileAppService.uploadFile(1L, token, file));
        }

        @Test
        @DisplayName("无效凭证应抛异常")
        void shouldRejectInvalidCredential() {
            MockMultipartFile file = new MockMultipartFile("file", "test.png",
                    "image/png", "hello".getBytes());

            assertThrows(Exception.class,
                    () -> fileAppService.uploadFile(1L, "invalid-token", file));
        }

        @Test
        @DisplayName("不支持的文件类型应抛异常")
        void shouldRejectUnsupportedType() {
            String token = credentialService.generateCredential(1L, 1, "ip-series", null);
            MockMultipartFile file = new MockMultipartFile("file", "test.txt",
                    "text/plain", "hello".getBytes());

            assertThrows(Exception.class,
                    () -> fileAppService.uploadFile(1L, token, file));
        }
    }

    @Nested
    @DisplayName("删除文件")
    class DeleteTests {

        @Test
        @DisplayName("成功软删除文件")
        void shouldSoftDeleteFile() {
            FileInfo fileInfo = FileInfo.reconstruct(1L, "test.png", "uuid.png",
                    "ip-series/20260612/uuid.png", "/static/ip-series/20260612/uuid.png",
                    "image/png", 100, "ip-series", 1L, LocalDateTime.now(), false, null);
            when(fileInfoRepository.findById(1L)).thenReturn(Optional.of(fileInfo));

            fileAppService.deleteFile(1L);

            verify(fileInfoRepository).save(any(FileInfo.class));
        }

        @Test
        @DisplayName("文件不存在应抛异常")
        void shouldThrowWhenFileNotFound() {
            when(fileInfoRepository.findById(999L)).thenReturn(Optional.empty());
            assertThrows(Exception.class, () -> fileAppService.deleteFile(999L));
        }
    }

    @Nested
    @DisplayName("查询文件")
    class QueryTests {

        @Test
        @DisplayName("成功查询单个文件")
        void shouldGetFileInfo() {
            FileInfo fileInfo = FileInfo.reconstruct(1L, "test.png", "uuid.png",
                    "ip-series/20260612/uuid.png", "/static/ip-series/20260612/uuid.png",
                    "image/png", 100, "ip-series", 1L, LocalDateTime.now(), false, null);
            when(fileInfoRepository.findById(1L)).thenReturn(Optional.of(fileInfo));

            FileInfoResponse response = fileAppService.getFileInfo(1L);

            assertNotNull(response);
            assertEquals(1L, response.getFileId());
            assertEquals("ip-series", response.getBasePath());
        }

        @Test
        @DisplayName("成功批量查询")
        void shouldBatchGetUrls() {
            FileInfo fileInfo = FileInfo.reconstruct(1L, "test.png", "uuid.png",
                    "ip-series/20260612/uuid.png", "/static/ip-series/20260612/uuid.png",
                    "image/png", 100, "ip-series", 1L, LocalDateTime.now(), false, null);
            when(fileInfoRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(fileInfo));

            List<FileInfoResponse> responses = fileAppService.batchGetUrls(List.of(1L));

            assertEquals(1, responses.size());
            assertEquals(1L, responses.get(0).getFileId());
        }
    }

    @Nested
    @DisplayName("metadata 写入")
    class MetadataTests {

        @Test
        @DisplayName("uploadFile 应将凭证 metadata 写入 FileInfo")
        void shouldWriteMetadataOnUpload() {
            Map<String, Object> metadata = Map.of("bizType", "IP_SERIES", "userId", 1L);
            String token = credentialService.generateCredential(1L, 1, "ip-series", metadata);

            StoredFile storedFile = new StoredFile("uuid.png", "ip-series/20260612/uuid.png",
                    "/static/ip-series/20260612/uuid.png", "image/png", 100);
            when(storageProvider.store(anyString(), anyString(), any(), any(long.class), anyString()))
                    .thenReturn(storedFile);

            FileInfo savedFileInfo = FileInfo.reconstruct(1L, "test.png", "uuid.png",
                    "ip-series/20260612/uuid.png", "/static/ip-series/20260612/uuid.png",
                    "image/png", 100, "ip-series", 1L, LocalDateTime.now(), false, metadata);
            ArgumentCaptor<FileInfo> captor = ArgumentCaptor.forClass(FileInfo.class);
            when(fileInfoRepository.save(captor.capture())).thenReturn(savedFileInfo);

            MockMultipartFile file = new MockMultipartFile("file", "test.png",
                    "image/png", "hello".getBytes());

            fileAppService.uploadFile(1L, token, file);

            FileInfo captured = captor.getValue();
            assertNotNull(captured.getMetadata());
            assertEquals("IP_SERIES", captured.getMetadata().get("bizType"));
            assertEquals(1L, captured.getMetadata().get("userId"));
        }

        @Test
        @DisplayName("batchUploadFiles 应将凭证 metadata 写入每个 FileInfo")
        void shouldWriteMetadataOnBatchUpload() {
            Map<String, Object> metadata = Map.of("bizType", "IP_SERIES", "userId", 1L);
            String token = credentialService.generateCredential(1L, 2, "ip-series", metadata);

            StoredFile storedFile = new StoredFile("uuid.png", "ip-series/20260612/uuid.png",
                    "/static/ip-series/20260612/uuid.png", "image/png", 100);
            when(storageProvider.store(anyString(), anyString(), any(), any(long.class), anyString()))
                    .thenReturn(storedFile);

            FileInfo savedFileInfo = FileInfo.reconstruct(1L, "test.png", "uuid.png",
                    "ip-series/20260612/uuid.png", "/static/ip-series/20260612/uuid.png",
                    "image/png", 100, "ip-series", 1L, LocalDateTime.now(), false, metadata);
            ArgumentCaptor<FileInfo> captor = ArgumentCaptor.forClass(FileInfo.class);
            when(fileInfoRepository.save(captor.capture())).thenReturn(savedFileInfo);

            MockMultipartFile file1 = new MockMultipartFile("files", "test1.png",
                    "image/png", "hello".getBytes());
            MockMultipartFile file2 = new MockMultipartFile("files", "test2.png",
                    "image/png", "world".getBytes());

            fileAppService.batchUploadFiles(1L, token, List.of(file1, file2));

            List<FileInfo> captured = captor.getAllValues();
            assertEquals(2, captured.size());
            for (FileInfo info : captured) {
                assertNotNull(info.getMetadata());
                assertEquals("IP_SERIES", info.getMetadata().get("bizType"));
                assertEquals(1L, info.getMetadata().get("userId"));
            }
        }
    }
}

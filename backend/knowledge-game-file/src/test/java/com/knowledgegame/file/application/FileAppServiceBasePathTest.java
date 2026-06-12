package com.knowledgegame.file.application;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.file.common.config.FileProperties;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * FileAppService basePath 格式校验专项测试
 */
@ExtendWith(MockitoExtension.class)
class FileAppServiceBasePathTest {

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
    @DisplayName("basePath 格式校验")
    class BasePathValidationTests {

        @Test
        @DisplayName("合法 basePath：小写字母（如 ip-series）")
        void shouldAccept_lowerCaseWithHyphen() {
            assertDoesNotThrow(() -> fileAppService.generateCredential(1L, 1, "ip-series"));
        }

        @Test
        @DisplayName("合法 basePath：纯小写字母（如 avatar）")
        void shouldAccept_lowerCaseLetters() {
            assertDoesNotThrow(() -> fileAppService.generateCredential(1L, 1, "avatar"));
        }

        @Test
        @DisplayName("合法 basePath：小写字母+数字+短横线（如 card-star-image）")
        void shouldAccept_lowerCaseWithNumbersAndHyphens() {
            assertDoesNotThrow(() -> fileAppService.generateCredential(1L, 1, "card-star-image"));
        }

        @Test
        @DisplayName("合法 basePath：小写字母+数字（如 image2）")
        void shouldAccept_lowerCaseWithNumbers() {
            assertDoesNotThrow(() -> fileAppService.generateCredential(1L, 1, "image2"));
        }

        @Test
        @DisplayName("空 basePath 被拒绝")
        void shouldReject_emptyBasePath() {
            Exception ex = assertThrows(Exception.class,
                    () -> fileAppService.generateCredential(1L, 1, ""));
            assertEquals(400, ((BusinessException) ex).getCode());
        }

        @Test
        @DisplayName("null basePath 被拒绝")
        void shouldReject_nullBasePath() {
            Exception ex = assertThrows(Exception.class,
                    () -> fileAppService.generateCredential(1L, 1, null));
            assertEquals(400, ((BusinessException) ex).getCode());
        }

        @Test
        @DisplayName("包含 \"..\" 的 basePath 被拒绝（路径遍历攻击防护）")
        void shouldReject_pathTraversal() {
            Exception ex = assertThrows(Exception.class,
                    () -> fileAppService.generateCredential(1L, 1, ".."));
            assertEquals(400, ((BusinessException) ex).getCode());
        }

        @Test
        @DisplayName("包含 \"../\" 的 basePath 被拒绝")
        void shouldReject_pathTraversalWithSlash() {
            Exception ex = assertThrows(Exception.class,
                    () -> fileAppService.generateCredential(1L, 1, "../etc"));
            assertEquals(400, ((BusinessException) ex).getCode());
        }

        @Test
        @DisplayName("包含 \"/\" 的 basePath 被拒绝")
        void shouldReject_slashInPath() {
            Exception ex = assertThrows(Exception.class,
                    () -> fileAppService.generateCredential(1L, 1, "ip/series"));
            assertEquals(400, ((BusinessException) ex).getCode());
        }

        @Test
        @DisplayName("大写字母的 basePath 被拒绝（如 IP_SERIES）")
        void shouldReject_upperCaseLetters() {
            Exception ex = assertThrows(Exception.class,
                    () -> fileAppService.generateCredential(1L, 1, "IP_SERIES"));
            assertEquals(400, ((BusinessException) ex).getCode());
        }

        @Test
        @DisplayName("包含下划线的 basePath 被拒绝")
        void shouldReject_underscore() {
            Exception ex = assertThrows(Exception.class,
                    () -> fileAppService.generateCredential(1L, 1, "ip_series"));
            assertEquals(400, ((BusinessException) ex).getCode());
        }

        @Test
        @DisplayName("以短横线开头的 basePath 被拒绝（如 -test）")
        void shouldReject_startWithHyphen() {
            Exception ex = assertThrows(Exception.class,
                    () -> fileAppService.generateCredential(1L, 1, "-test"));
            assertEquals(400, ((BusinessException) ex).getCode());
        }

        @Test
        @DisplayName("包含空格的 basePath 被拒绝")
        void shouldReject_spaceInPath() {
            Exception ex = assertThrows(Exception.class,
                    () -> fileAppService.generateCredential(1L, 1, "ip series"));
            assertEquals(400, ((BusinessException) ex).getCode());
        }

        @Test
        @DisplayName("包含特殊字符的 basePath 被拒绝")
        void shouldReject_specialCharacters() {
            Exception ex = assertThrows(Exception.class,
                    () -> fileAppService.generateCredential(1L, 1, "ip@series"));
            assertEquals(400, ((BusinessException) ex).getCode());
        }

        @Test
        @DisplayName("纯数字的 basePath 合法")
        void shouldAccept_pureNumbers() {
            assertDoesNotThrow(() -> fileAppService.generateCredential(1L, 1, "123"));
        }

        @Test
        @DisplayName("单字母 basePath 合法")
        void shouldAccept_singleLetter() {
            assertDoesNotThrow(() -> fileAppService.generateCredential(1L, 1, "a"));
        }

        @Test
        @DisplayName("单数字 basePath 合法")
        void shouldAccept_singleDigit() {
            assertDoesNotThrow(() -> fileAppService.generateCredential(1L, 1, "1"));
        }
    }
}

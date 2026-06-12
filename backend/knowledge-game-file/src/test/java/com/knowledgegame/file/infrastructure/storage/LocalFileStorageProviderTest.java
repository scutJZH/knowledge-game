package com.knowledgegame.file.infrastructure.storage;

import com.knowledgegame.file.domain.model.StoredFile;
import com.knowledgegame.file.domain.port.outbound.FileStorageProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地磁盘存储测试
 */
class LocalFileStorageProviderTest {

    private Path tempDir;
    private FileStorageProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("file-test");
        provider = new LocalFileStorageProvider(tempDir.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        // ignore
                    }
                });
    }

    @Test
    @DisplayName("应正确存储文件到磁盘")
    void shouldStoreFile() {
        byte[] data = "hello world".getBytes();
        InputStream input = new ByteArrayInputStream(data);

        StoredFile result = provider.store("ip-series", "test.png", input, data.length, "image/png");

        assertNotNull(result.storedName());
        assertTrue(result.filePath().startsWith("ip-series/"));
        assertTrue(result.url().startsWith("/static/ip-series/"));
        assertEquals("image/png", result.contentType());
        assertEquals(data.length, result.fileSize());

        // 验证文件确实写入磁盘
        Path storedPath = tempDir.resolve(result.filePath());
        assertTrue(Files.exists(storedPath));
    }

    @Test
    @DisplayName("应根据 contentType 推断扩展名")
    void shouldInferExtensionFromContentType() {
        InputStream input = new ByteArrayInputStream("data".getBytes());
        StoredFile result = provider.store("avatar", "photo.jpeg", input, 4, "image/png");
        assertTrue(result.storedName().endsWith(".png"));
    }

    @Test
    @DisplayName("未知 contentType 应无扩展名")
    void shouldStoreFileWithoutExtensionForUnknownType() {
        InputStream input = new ByteArrayInputStream("data".getBytes());
        StoredFile result = provider.store("avatar", "noext", input, 4, "image/unknown");
        assertFalse(result.storedName().contains("."));
    }

    @Test
    @DisplayName("应正确删除已存在的文件")
    void shouldDeleteExistingFile() {
        InputStream input = new ByteArrayInputStream("data".getBytes());
        StoredFile result = provider.store("ip-series", "test.png", input, 4, "image/png");

        Path storedPath = tempDir.resolve(result.filePath());
        assertTrue(Files.exists(storedPath));

        provider.delete(result.filePath());
        assertTrue(Files.notExists(storedPath));
    }

    @Test
    @DisplayName("删除不存在的文件不应抛异常")
    void shouldNotThrowOnDeleteNonExistent() {
        // 不应抛异常
        provider.delete("non/existent/file.png");
    }

    @Test
    @DisplayName("应正确加载文件内容")
    void shouldLoadFile() throws IOException {
        byte[] data = "hello world".getBytes();
        InputStream input = new ByteArrayInputStream(data);
        StoredFile result = provider.store("ip-series", "test.png", input, data.length, "image/png");

        try (InputStream loaded = provider.load(result.filePath())) {
            byte[] loadedData = loaded.readAllBytes();
            assertEquals(data.length, loadedData.length);
        }
    }
}

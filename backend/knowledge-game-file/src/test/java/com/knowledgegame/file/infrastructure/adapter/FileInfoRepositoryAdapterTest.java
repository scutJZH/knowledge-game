package com.knowledgegame.file.infrastructure.adapter;

import com.knowledgegame.file.domain.model.BizType;
import com.knowledgegame.file.domain.model.FileInfo;
import com.knowledgegame.file.domain.model.StoredFile;
import com.knowledgegame.file.infrastructure.adapter.repoadapter.FileInfoRepositoryAdapter;
import com.knowledgegame.file.infrastructure.db.converter.FileInfoConverter;
import com.knowledgegame.file.infrastructure.db.entity.FileInfoPO;
import com.knowledgegame.file.infrastructure.db.repository.FileInfoJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 仓储适配器测试
 */
@ExtendWith(MockitoExtension.class)
class FileInfoRepositoryAdapterTest {

    @Mock
    private FileInfoJpaRepository jpaRepository;

    private FileInfoRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new FileInfoRepositoryAdapter(jpaRepository, FileInfoConverter.INSTANCE);
    }

    @Test
    @DisplayName("save 应正确转换并保存")
    void shouldSaveAndReturnDomain() {
        FileInfo fileInfo = FileInfo.create("test.png",
                new StoredFile("uuid.png", "IP_SERIES/20260612/uuid.png", "/static/IP_SERIES/20260612/uuid.png", "image/png", 1024),
                BizType.IP_SERIES, 1L);

        FileInfoPO savedPO = new FileInfoPO();
        savedPO.setId(1L);
        savedPO.setOriginalName("test.png");
        savedPO.setStoredName("uuid.png");
        savedPO.setFilePath("IP_SERIES/20260612/uuid.png");
        savedPO.setUrl("/static/IP_SERIES/20260612/uuid.png");
        savedPO.setContentType("image/png");
        savedPO.setFileSize(1024L);
        savedPO.setBizType("IP_SERIES");
        savedPO.setUploaderId(1L);
        savedPO.setCreatedAt(LocalDateTime.now());
        savedPO.setDeleted(false);

        when(jpaRepository.save(any(FileInfoPO.class))).thenReturn(savedPO);

        FileInfo result = adapter.save(fileInfo);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("test.png", result.getOriginalName());
        assertEquals(BizType.IP_SERIES, result.getBizType());
        verify(jpaRepository).save(any(FileInfoPO.class));
    }

    @Test
    @DisplayName("findById 应排除已删除记录")
    void shouldExcludeDeletedOnFindById() {
        when(jpaRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.empty());
        Optional<FileInfo> result = adapter.findById(1L);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("deleteById 应调用 JPA 删除")
    void shouldDeleteById() {
        adapter.deleteById(1L);
        verify(jpaRepository).deleteById(1L);
    }

    @Test
    @DisplayName("findAllByIdIn 应排除已删除记录")
    void shouldExcludeDeletedOnBatchQuery() {
        when(jpaRepository.findAllByIdInAndDeletedFalse(List.of(1L, 2L))).thenReturn(List.of());
        List<FileInfo> result = adapter.findAllByIdIn(List.of(1L, 2L));
        assertTrue(result.isEmpty());
    }
}

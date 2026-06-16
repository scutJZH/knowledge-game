package com.knowledgegame.file.infrastructure.db.converter;

import com.knowledgegame.file.domain.model.FileInfo;
import com.knowledgegame.file.domain.model.StoredFile;
import com.knowledgegame.file.infrastructure.db.entity.FileInfoPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * FileInfoConverter 单元测试 — 验证 PO ↔ Domain 双向映射中 metadata 不丢失
 */
class FileInfoConverterTest {

    @Nested
    @DisplayName("toDomain（PO → 领域模型）")
    class ToDomainTests {

        @Test
        @DisplayName("PO 的 metadata 应传递到领域模型")
        void shouldMapMetadataToDomain() {
            FileInfoPO po = new FileInfoPO();
            po.setId(1L);
            po.setOriginalName("test.png");
            po.setStoredName("uuid.png");
            po.setFilePath("ip-series/20260612/uuid.png");
            po.setUrl("/static/ip-series/20260612/uuid.png");
            po.setContentType("image/png");
            po.setFileSize(100L);
            po.setBasePath("ip-series");
            po.setUploaderId(1L);
            po.setCreatedAt(java.time.LocalDateTime.now());
            po.setDeleted(false);
            po.setMetadata(Map.of("bizType", "IP_SERIES", "userId", 1L));

            FileInfo domain = FileInfoConverter.INSTANCE.toDomain(po);

            assertNotNull(domain.getMetadata());
            assertEquals("IP_SERIES", domain.getMetadata().get("bizType"));
            assertEquals(1L, domain.getMetadata().get("userId"));
        }

        @Test
        @DisplayName("PO 的 metadata 为 null 时领域模型也应为 null")
        void shouldMapNullMetadataToDomain() {
            FileInfoPO po = new FileInfoPO();
            po.setId(1L);
            po.setOriginalName("test.png");
            po.setStoredName("uuid.png");
            po.setFilePath("ip-series/20260612/uuid.png");
            po.setUrl("/static/ip-series/20260612/uuid.png");
            po.setContentType("image/png");
            po.setFileSize(100L);
            po.setBasePath("ip-series");
            po.setUploaderId(1L);
            po.setCreatedAt(java.time.LocalDateTime.now());
            po.setDeleted(false);
            po.setMetadata(null);

            FileInfo domain = FileInfoConverter.INSTANCE.toDomain(po);

            assertNull(domain.getMetadata());
        }
    }

    @Nested
    @DisplayName("toPO（领域模型 → PO）")
    class ToPOTests {

        @Test
        @DisplayName("领域模型的 metadata 应传递到 PO")
        void shouldMapMetadataToPO() {
            Map<String, Object> metadata = Map.of("bizType", "IP_SERIES");
            FileInfo domain = FileInfo.create("test.png",
                    new StoredFile("uuid.png", "ip-series/20260612/uuid.png",
                            "/static/ip-series/20260612/uuid.png", "image/png", 100),
                    "ip-series", 1L, metadata);

            FileInfoPO po = FileInfoConverter.INSTANCE.toPO(domain);

            assertNotNull(po.getMetadata());
            assertEquals("IP_SERIES", po.getMetadata().get("bizType"));
        }

        @Test
        @DisplayName("领域模型 metadata 为 null 时 PO 也应为 null")
        void shouldMapNullMetadataToPO() {
            FileInfo domain = FileInfo.create("test.png",
                    new StoredFile("uuid.png", "ip-series/20260612/uuid.png",
                            "/static/ip-series/20260612/uuid.png", "image/png", 100),
                    "ip-series", 1L, null);

            FileInfoPO po = FileInfoConverter.INSTANCE.toPO(domain);

            assertNull(po.getMetadata());
        }
    }
}

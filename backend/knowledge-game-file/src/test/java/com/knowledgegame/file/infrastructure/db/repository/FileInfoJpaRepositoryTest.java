package com.knowledgegame.file.infrastructure.db.repository;

import com.knowledgegame.file.infrastructure.db.entity.FileInfoPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class FileInfoJpaRepositoryTest {

    @Autowired
    private FileInfoJpaRepository repository;

    @Test
    @DisplayName("metadata JSON 列保存后查询不丢失")
    void shouldPreserveMetadataAfterSave() {
        FileInfoPO po = new FileInfoPO();
        po.setOriginalName("test.png");
        po.setStoredName("uuid.png");
        po.setFilePath("test/uuid.png");
        po.setUrl("/static/test/uuid.png");
        po.setContentType("image/png");
        po.setFileSize(100L);
        po.setBasePath("test");
        po.setUploaderId(1L);
        po.setCreatedAt(LocalDateTime.now());
        po.setDeleted(false);
        po.setMetadata(Map.of("bizType", "IP_SERIES", "userId", 1));

        FileInfoPO saved = repository.save(po);
        assertNotNull(saved.getId());

        FileInfoPO found = repository.findById(saved.getId()).orElseThrow();
        Map<String, Object> metadata = found.getMetadata();
        assertNotNull(metadata);
        assertEquals("IP_SERIES", metadata.get("bizType"));
    }

    @Test
    @DisplayName("metadata 中数字值经 JSON 反序列化后需用 Number.longValue() 获取")
    void shouldHandleIntegerLongTypeMismatch() {
        FileInfoPO po = new FileInfoPO();
        po.setOriginalName("test.png");
        po.setStoredName("uuid.png");
        po.setFilePath("test/uuid.png");
        po.setUrl("/static/test/uuid.png");
        po.setContentType("image/png");
        po.setFileSize(100L);
        po.setBasePath("test");
        po.setUploaderId(1L);
        po.setCreatedAt(LocalDateTime.now());
        po.setDeleted(false);
        // 小数字写入，JSON 反序列化时 Jackson 默认解析为 Integer
        po.setMetadata(Map.of("bizType", "IP_SERIES", "userId", 1));

        FileInfoPO saved = repository.save(po);
        FileInfoPO found = repository.findById(saved.getId()).orElseThrow();
        Object rawUserId = found.getMetadata().get("userId");
        assertNotNull(rawUserId);

        // 关键：小数字经 JSON 列反序列化后是 Integer，直接用 Long.equals(Integer) 为 false
        // 必须转为 Number.longValue() 再比较
        Long userId = rawUserId instanceof Number ? ((Number) rawUserId).longValue() : null;
        assertEquals(1L, userId);
        // 验证原始类型确实是 Integer（这是 bug 的根因）
        assertTrue(rawUserId instanceof Integer, "小数字经 JSON 反序列化后应为 Integer");
    }
}

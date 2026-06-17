package com.knowledgegame.core.infrastructure.db.converter;

import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.entity.IpSeries;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.infrastructure.db.entity.IpSeriesPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class IpSeriesConverterTest {

    @Nested
    @DisplayName("toDomain（PO → 领域模型）")
    class ToDomainTests {

        @Test
        @DisplayName("双字段 PO 应映射为 FileRef")
        void shouldMapDualFieldsToFileRef() {
            IpSeriesPO po = buildPO(1L, 10L, "/static/cover.jpg");
            IpSeries domain = IpSeriesConverter.INSTANCE.toDomain(po);

            assertNotNull(domain.getCoverImage());
            assertEquals(10L, domain.getCoverImage().fileId());
            assertEquals("/static/cover.jpg", domain.getCoverImage().url());
        }

        @Test
        @DisplayName("双字段均为 null 时 FileRef 为 null")
        void shouldReturnNullFileRefWhenBothNull() {
            IpSeriesPO po = buildPO(1L, null, null);
            IpSeries domain = IpSeriesConverter.INSTANCE.toDomain(po);

            assertNull(domain.getCoverImage());
        }

        @Test
        @DisplayName("null PO 返回 null")
        void shouldReturnNullForNullPO() {
            assertNull(IpSeriesConverter.INSTANCE.toDomain(null));
        }
    }

    @Nested
    @DisplayName("toPO（领域模型 → PO）")
    class ToPOTests {

        @Test
        @DisplayName("FileRef 应映射为双字段 PO")
        void shouldMapFileRefToDualFields() {
            IpSeries domain = IpSeries.reconstruct(1L, "CODE", "名称", "描述",
                    FileRef.of(10L, "/static/cover.jpg"),
                    IpSeriesStatus.ACTIVE,
                    LocalDateTime.now(), LocalDateTime.now());

            IpSeriesPO po = IpSeriesConverter.INSTANCE.toPO(domain);

            assertEquals(10L, po.getCoverImageFileId());
            assertEquals("/static/cover.jpg", po.getCoverImageUrl());
        }

        @Test
        @DisplayName("null FileRef 应映射为双 null PO 字段")
        void shouldMapNullFileRefToNullFields() {
            IpSeries domain = IpSeries.reconstruct(1L, "CODE", "名称", "描述",
                    null, IpSeriesStatus.ACTIVE,
                    LocalDateTime.now(), LocalDateTime.now());

            IpSeriesPO po = IpSeriesConverter.INSTANCE.toPO(domain);

            assertNull(po.getCoverImageFileId());
            assertNull(po.getCoverImageUrl());
        }
    }

    @Nested
    @DisplayName("updatePO（领域模型 → 已有 PO 更新）")
    class UpdatePOTests {

        @Test
        @DisplayName("非 null FileRef 应显式赋值 PO 双字段")
        void shouldSetDualFieldsWhenFileRefNonNull() {
            IpSeriesPO po = buildPO(1L, 1L, "/static/old.jpg");
            IpSeries domain = IpSeries.reconstruct(1L, "CODE", "名称", "描述",
                    FileRef.of(99L, "/static/new.jpg"),
                    IpSeriesStatus.ACTIVE, null, null);

            IpSeriesConverter.INSTANCE.updatePO(po, domain);

            assertEquals(99L, po.getCoverImageFileId());
            assertEquals("/static/new.jpg", po.getCoverImageUrl());
        }

        @Test
        @DisplayName("null FileRef 应保留 PO 原值")
        void shouldKeepOldValuesWhenFileRefNull() {
            IpSeriesPO po = buildPO(1L, 1L, "/static/old.jpg");
            IpSeries domain = IpSeries.reconstruct(1L, "CODE", "名称", "描述",
                    null, IpSeriesStatus.ACTIVE, null, null);

            IpSeriesConverter.INSTANCE.updatePO(po, domain);

            assertEquals(1L, po.getCoverImageFileId());
            assertEquals("/static/old.jpg", po.getCoverImageUrl());
        }
    }

    private IpSeriesPO buildPO(Long id, Long fileId, String url) {
        return IpSeriesPO.builder()
                .id(id)
                .code("CODE")
                .name("名称")
                .description("描述")
                .coverImageFileId(fileId)
                .coverImageUrl(url)
                .status(IpSeriesStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}

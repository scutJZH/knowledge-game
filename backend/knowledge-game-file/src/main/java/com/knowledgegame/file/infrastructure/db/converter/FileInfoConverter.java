package com.knowledgegame.file.infrastructure.db.converter;

import com.knowledgegame.file.domain.model.FileInfo;
import com.knowledgegame.file.infrastructure.db.entity.FileInfoPO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * FileInfo PO ↔ Domain 转换器
 */
@Mapper
public interface FileInfoConverter {

    FileInfoConverter INSTANCE = Mappers.getMapper(FileInfoConverter.class);

    @Mapping(target = "basePath", source = "basePath")
    @Mapping(target = "deleted", expression = "java(fileInfo.isDeleted())")
    FileInfoPO toPO(FileInfo fileInfo);

    /**
     * PO → Domain：使用 reconstruct 静态方法，因为领域对象无公共构造器
     */
    default FileInfo toDomain(FileInfoPO po) {
        if (po == null) {
            return null;
        }
        return FileInfo.reconstruct(
                po.getId(),
                po.getOriginalName(),
                po.getStoredName(),
                po.getFilePath(),
                po.getUrl(),
                po.getContentType(),
                po.getFileSize(),
                po.getBasePath(),
                po.getUploaderId(),
                po.getCreatedAt(),
                po.getDeleted(),
                po.getMetadata()
        );
    }

    List<FileInfo> toDomainList(List<FileInfoPO> pos);
}

package com.knowledgegame.app.api.assembler;

import com.knowledgegame.app.api.dto.QuestionListResponse;
import com.knowledgegame.core.domain.model.entity.Question;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Mapper
public interface QuestionAssembler {

    QuestionAssembler INSTANCE = Mappers.getMapper(QuestionAssembler.class);

    default QuestionListResponse toListResponse(Question q) {
        QuestionListResponse r = new QuestionListResponse();
        r.setId(q.getId());
        String content = q.getContent();
        r.setTitle(content != null && content.length() > 80 ? content.substring(0, 80) : content);
        r.setFullText(content);
        r.setAnswer(q.getAnswer());
        r.setDifficulty(q.getDifficulty() != null ? q.getDifficulty().getLevel() : null);
        r.setType(q.getType() != null ? q.getType().name() : null);
        r.setCreatedAt(toEpochMilli(q.getCreatedAt()));
        return r;
    }

    default Long toEpochMilli(LocalDateTime dt) {
        return dt == null ? null : dt.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
    }
}

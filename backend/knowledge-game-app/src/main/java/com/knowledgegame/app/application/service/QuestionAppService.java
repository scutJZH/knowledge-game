package com.knowledgegame.app.application.service;

import com.knowledgegame.app.api.assembler.QuestionAssembler;
import com.knowledgegame.app.api.dto.QuestionListResponse;
import com.knowledgegame.app.api.dto.QuestionPageResponse;
import com.knowledgegame.core.domain.model.domainenum.QuestionStatus;
import com.knowledgegame.core.domain.model.entity.Question;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.port.outbound.QuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QuestionAppService {

    private final QuestionRepository questionRepository;

    public QuestionAppService(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    @Transactional(readOnly = true)
    public QuestionPageResponse listByCategory(Long categoryId, int page, int size) {
        PageResult<Question> pageResult = questionRepository.findByConditions(
                null, null, null, categoryId, null,
                QuestionStatus.ACTIVE,
                null, page - 1, size);
        List<QuestionListResponse> content = pageResult.getContent().stream()
                .map(QuestionAssembler.INSTANCE::toListResponse)
                .toList();
        QuestionPageResponse response = new QuestionPageResponse();
        response.setContent(content);
        response.setTotalElements(pageResult.getTotalElements());
        response.setTotalPages(pageResult.getTotalPages());
        return response;
    }
}

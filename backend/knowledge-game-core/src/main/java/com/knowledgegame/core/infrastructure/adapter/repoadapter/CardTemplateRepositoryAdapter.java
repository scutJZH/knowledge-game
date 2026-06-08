package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import com.knowledgegame.core.domain.model.entity.CardTemplate;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.port.outbound.CardTemplateRepositoryPort;
import com.knowledgegame.core.infrastructure.db.converter.CardTemplateConverter;
import com.knowledgegame.core.infrastructure.db.entity.CardTemplatePO;
import com.knowledgegame.core.infrastructure.db.repository.CardTemplateJpaRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 卡牌模板仓储适配器（实现领域层出端口，注入 JPA Repository）
 */
@Repository
public class CardTemplateRepositoryAdapter implements CardTemplateRepositoryPort {

    private final CardTemplateJpaRepository cardTemplateJpaRepository;

    public CardTemplateRepositoryAdapter(CardTemplateJpaRepository cardTemplateJpaRepository) {
        this.cardTemplateJpaRepository = cardTemplateJpaRepository;
    }

    @Override
    public CardTemplate save(CardTemplate cardTemplate) {
        if (cardTemplate.getId() == null) {
            CardTemplatePO po = CardTemplateConverter.INSTANCE.toPO(cardTemplate);
            CardTemplatePO saved = cardTemplateJpaRepository.save(po);
            return CardTemplateConverter.INSTANCE.toDomain(saved);
        }
        CardTemplatePO existing = cardTemplateJpaRepository.findById(cardTemplate.getId())
                .orElseThrow(() -> new IllegalArgumentException("卡牌模板不存在: " + cardTemplate.getId()));
        CardTemplateConverter.INSTANCE.updatePO(existing, cardTemplate);
        CardTemplatePO saved = cardTemplateJpaRepository.save(existing);
        return CardTemplateConverter.INSTANCE.toDomain(saved);
    }

    @Override
    public Optional<CardTemplate> findById(Long id) {
        return cardTemplateJpaRepository.findById(id)
                .map(CardTemplateConverter.INSTANCE::toDomain);
    }

    @Override
    public Optional<CardTemplate> findByCode(String code) {
        return cardTemplateJpaRepository.findByCode(code)
                .map(CardTemplateConverter.INSTANCE::toDomain);
    }

    @Override
    public PageResult<CardTemplate> findByConditions(String name, Long ipSeriesId,
                                                     CardRarity rarity, CardTemplateStatus status,
                                                     int pageNumber, int pageSize) {
        Specification<CardTemplatePO> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(root.get("name"), "%" + name + "%"));
            }
            if (ipSeriesId != null) {
                predicates.add(cb.equal(root.get("ipSeriesId"), ipSeriesId));
            }
            if (rarity != null) {
                predicates.add(cb.equal(root.get("rarity"), rarity));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<CardTemplatePO> springPage = cardTemplateJpaRepository.findAll(spec,
                PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResult.<CardTemplate>builder()
                .content(springPage.getContent().stream()
                        .map(CardTemplateConverter.INSTANCE::toDomain).toList())
                .totalElements(springPage.getTotalElements())
                .pageNumber(springPage.getNumber())
                .pageSize(springPage.getSize())
                .totalPages(springPage.getTotalPages())
                .build();
    }

    @Override
    public boolean existsById(Long id) {
        return cardTemplateJpaRepository.existsById(id);
    }
}

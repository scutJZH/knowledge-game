package com.knowledgegame.app.application.service;

import com.knowledgegame.app.api.assembler.KnowledgeCategoryAssembler;
import com.knowledgegame.app.api.dto.response.KnowledgeCategoryTreeResponse;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识点分类用户端应用服务（流程编排 + 返回 DTO）
 */
@Service
public class KnowledgeCategoryAppService {

    private final KnowledgeCategoryRepositoryPort categoryRepositoryPort;

    public KnowledgeCategoryAppService(KnowledgeCategoryRepositoryPort categoryRepositoryPort) {
        this.categoryRepositoryPort = categoryRepositoryPort;
    }

    /**
     * 查询 ACTIVE 分类树
     */
    public List<KnowledgeCategoryTreeResponse> tree() {
        List<KnowledgeCategory> all = categoryRepositoryPort.findAll();
        List<KnowledgeCategory> activeOnly = all.stream()
                .filter(c -> c.getStatus() == KnowledgeCategoryStatus.ACTIVE)
                .toList();
        return buildTree(activeOnly);
    }

    /**
     * 从平铺列表构建嵌套树，同级按 sortOrder 升序
     */
    private List<KnowledgeCategoryTreeResponse> buildTree(List<KnowledgeCategory> categories) {
        Map<Long, KnowledgeCategoryTreeResponse> nodeMap = new LinkedHashMap<>();
        List<KnowledgeCategoryTreeResponse> roots = new ArrayList<>();
        for (KnowledgeCategory category : categories) {
            nodeMap.put(category.getId(), KnowledgeCategoryAssembler.INSTANCE.toTreeNode(category));
        }
        // 所有节点初始化 children 为空列表，叶子节点保持空列表而非 null
        for (KnowledgeCategoryTreeResponse node : nodeMap.values()) {
            node.setChildren(new ArrayList<>());
        }
        for (KnowledgeCategory category : categories) {
            KnowledgeCategoryTreeResponse node = nodeMap.get(category.getId());
            if (category.getParentId() == null) {
                roots.add(node);
            } else {
                KnowledgeCategoryTreeResponse parent = nodeMap.get(category.getParentId());
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    roots.add(node);
                }
            }
        }
        sortChildren(roots);
        return roots;
    }

    /**
     * 递归排序子节点（按 sortOrder 升序）
     */
    private void sortChildren(List<KnowledgeCategoryTreeResponse> nodes) {
        if (nodes == null) return;
        nodes.sort((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()));
        for (KnowledgeCategoryTreeResponse node : nodes) {
            sortChildren(node.getChildren());
        }
    }
}

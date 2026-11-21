package com.example.ordering.rules;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LoveRuleService {

    private final LoveRuleRepository loveRuleRepository;

    public LoveRuleService(LoveRuleRepository loveRuleRepository) {
        this.loveRuleRepository = loveRuleRepository;
    }

    @Transactional(readOnly = true)
    public List<LoveRuleResponse> listRules() {
        return loveRuleRepository.findAllByOrderBySortOrderAscIdAsc()
                .stream()
                .map(LoveRuleResponse::from)
                .toList();
    }

    @Transactional
    public LoveRuleResponse createRule(LoveRuleRequest request) {
        LoveRuleEntity rule = new LoveRuleEntity();
        rule.setContent(normalizeContent(request.content()));
        rule.setSortOrder(normalizeSortOrder(request.sortOrder(), nextSortOrder()));
        LoveRuleEntity saved = loveRuleRepository.save(rule);
        return LoveRuleResponse.from(saved);
    }

    @Transactional
    public LoveRuleResponse updateRule(Long id, LoveRuleRequest request) {
        LoveRuleEntity rule = loveRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("守则不存在"));
        rule.setContent(normalizeContent(request.content()));
        if (request.sortOrder() != null) {
            rule.setSortOrder(normalizeSortOrder(request.sortOrder(), rule.getSortOrder()));
        }
        rule.setUpdatedAt(LocalDateTime.now());
        return LoveRuleResponse.from(loveRuleRepository.save(rule));
    }

    @Transactional
    public void deleteRule(Long id) {
        LoveRuleEntity rule = loveRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("守则不存在"));
        loveRuleRepository.delete(rule);
    }

    private int nextSortOrder() {
        return loveRuleRepository.findTopByOrderBySortOrderDesc()
                .map(rule -> rule.getSortOrder() + 1)
                .orElse(1);
    }

    private String normalizeContent(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("守则内容不能为空");
        }
        return normalized;
    }

    private int normalizeSortOrder(Integer requestedSortOrder, int fallback) {
        if (requestedSortOrder == null) {
            return fallback;
        }
        return Math.max(1, requestedSortOrder);
    }
}

package org.example.springboot.service.qa;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 证据融合排序服务，当前采用可解释规则，后续可替换为 rerank 模型。
 */
@Service
public class QaEvidenceRankerService {

    public QaRankingResult rank(List<QaEvidenceItem> evidenceItems) {
        Map<String, QaEvidenceItem> unique = new LinkedHashMap<>();
        for (QaEvidenceItem item : evidenceItems) {
            String key = StringUtils.hasText(item.getTitle()) ? item.getTitle() : item.getEvidenceId();
            unique.putIfAbsent(key, item);
        }
        List<QaEvidenceItem> ranked = new ArrayList<>(unique.values());
        ranked.sort(Comparator.comparing(item -> item.getScore() == null ? 0D : item.getScore(), Comparator.reverseOrder()));
        double confidence = ranked.stream()
                .map(QaEvidenceItem::getConfidence)
                .filter(value -> value != null)
                .findFirst()
                .orElse(0.35D);
        return QaRankingResult.builder()
                .rankedEvidence(ranked)
                .deduplicatedCount(evidenceItems.size() - ranked.size())
                .conflictCount(0)
                .confidence(confidence)
                .rankingSummary(ranked.isEmpty() ? "未检索到可靠证据" : "已完成证据去重与来源权重排序")
                .build();
    }
}

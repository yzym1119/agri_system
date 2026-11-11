package com.agri_system.domain.agronomy.assistant;

import java.time.OffsetDateTime;
import java.util.List;

/** 返回外部不可信资料候选；不等于已审核或允许向客户发布的引用。 */
public interface AgronomySearchGateway {
    record Candidate(String title, String url, String excerpt, boolean excerptTruncated, OffsetDateTime publishedAt) {
        @Override public String toString() { return "AssistantSource[外部资料隐藏]"; }
    }
    List<Candidate> search(String query);
}

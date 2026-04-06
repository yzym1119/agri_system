package com.agri_system.domain.performance.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Table("performance_event_record")
public class PerformanceEventRecord {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String eventKey;

    private String eventType;

    private Long bizId;

    private LocalDateTime createdAt;
}


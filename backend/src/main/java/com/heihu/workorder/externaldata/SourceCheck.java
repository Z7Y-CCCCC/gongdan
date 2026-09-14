package com.heihu.workorder.externaldata;

import javax.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_data_source_checks", indexes = @Index(name = "idx_source_check_history", columnList = "factory_id,source_id,id"))
public class SourceCheck {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "factory_id", nullable = false) public Long factoryId;
    @Column(name = "source_id", nullable = false) public Long sourceId;
    @Column(name = "config_version", nullable = false) public Long configVersion;
    @Column(nullable = false, length = 24) public String status = "CHECKING";
    @Column(name = "error_code", length = 32) public String errorCode;
    @Column(length = 300) public String message;
    @Column(name = "http_status") public Integer httpStatus;
    @Column(name = "duration_ms") public Long durationMs;
    @Column(name = "started_at", nullable = false) public Instant startedAt = Instant.now();
    @Column(name = "finished_at") public Instant finishedAt;
    @Column(name = "trace_id", nullable = false, length = 36) public String traceId = UUID.randomUUID().toString();
}

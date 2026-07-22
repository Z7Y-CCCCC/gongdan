package com.heihu.workorder.service;

import com.heihu.workorder.repository.ProductOperationLogRepository;
import com.heihu.workorder.repository.SystemImportLogRepository;
import com.heihu.workorder.repository.SystemOperationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Keeps operational log tables bounded while retaining a configurable audit window.
 * Runtime file retention is handled by Logback and the host maintenance scripts.
 */
@Service
public class SystemLogRetentionService {

    private static final Logger log = LoggerFactory.getLogger(SystemLogRetentionService.class);

    private final SystemImportLogRepository importLogRepository;
    private final SystemOperationLogRepository operationLogRepository;
    private final ProductOperationLogRepository productOperationLogRepository;
    private final int retentionDays;

    public SystemLogRetentionService(
            SystemImportLogRepository importLogRepository,
            SystemOperationLogRepository operationLogRepository,
            ProductOperationLogRepository productOperationLogRepository,
            @Value("${app.system-log.retention-days:365}") int retentionDays
    ) {
        this.importLogRepository = importLogRepository;
        this.operationLogRepository = operationLogRepository;
        this.productOperationLogRepository = productOperationLogRepository;
        this.retentionDays = Math.max(1, retentionDays);
    }

    @Scheduled(cron = "${app.system-log.cleanup-cron:0 30 3 * * *}")
    @Transactional
    public int cleanupExpiredLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int importLogs = importLogRepository.deleteCreatedBefore(cutoff);
        int operationLogs = operationLogRepository.deleteCreatedBefore(cutoff);
        int productLogs = productOperationLogRepository.deleteCreatedBefore(cutoff);
        int total = importLogs + operationLogs + productLogs;
        log.info("清理过期操作日志：cutoff={}, importLogs={}, operationLogs={}, productLogs={}, total={}",
                cutoff, importLogs, operationLogs, productLogs, total);
        return total;
    }
}

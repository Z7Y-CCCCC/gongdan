package com.heihu.workorder.service;

import com.heihu.workorder.repository.ProductOperationLogRepository;
import com.heihu.workorder.repository.SystemImportLogRepository;
import com.heihu.workorder.repository.SystemOperationLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemLogRetentionServiceTest {

    @Mock
    private SystemImportLogRepository importLogRepository;

    @Mock
    private SystemOperationLogRepository operationLogRepository;

    @Mock
    private ProductOperationLogRepository productOperationLogRepository;

    @Test
    void cleanupDeletesAllOperationalLogTablesUsingConfiguredRetentionWindow() {
        when(importLogRepository.deleteCreatedBefore(any(LocalDateTime.class))).thenReturn(2);
        when(operationLogRepository.deleteCreatedBefore(any(LocalDateTime.class))).thenReturn(3);
        when(productOperationLogRepository.deleteCreatedBefore(any(LocalDateTime.class))).thenReturn(4);

        SystemLogRetentionService service = new SystemLogRetentionService(
                importLogRepository,
                operationLogRepository,
                productOperationLogRepository,
                30
        );

        int removed = service.cleanupExpiredLogs();

        assertThat(removed).isEqualTo(9);
        ArgumentCaptor<LocalDateTime> importCutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(importLogRepository).deleteCreatedBefore(importCutoff.capture());
        verify(operationLogRepository).deleteCreatedBefore(any(LocalDateTime.class));
        verify(productOperationLogRepository).deleteCreatedBefore(any(LocalDateTime.class));
        assertThat(importCutoff.getValue()).isBetween(
                LocalDateTime.now().minusDays(31),
                LocalDateTime.now().minusDays(29)
        );
    }

    @Test
    void retentionDaysNeverBecomesZeroOrNegative() {
        when(importLogRepository.deleteCreatedBefore(any(LocalDateTime.class))).thenReturn(0);
        when(operationLogRepository.deleteCreatedBefore(any(LocalDateTime.class))).thenReturn(0);
        when(productOperationLogRepository.deleteCreatedBefore(any(LocalDateTime.class))).thenReturn(0);

        SystemLogRetentionService service = new SystemLogRetentionService(
                importLogRepository,
                operationLogRepository,
                productOperationLogRepository,
                0
        );

        service.cleanupExpiredLogs();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(importLogRepository).deleteCreatedBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(
                LocalDateTime.now().minusDays(2),
                LocalDateTime.now()
        );
    }
}

package com.heihu.workorder.service;

import com.heihu.workorder.dto.*;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.SystemImportLog;
import com.heihu.workorder.model.SystemOperationLog;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.SystemImportLogRepository;
import com.heihu.workorder.repository.SystemOperationLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Transactional
public class SystemLogService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SystemImportLogRepository importLogRepository;
    private final SystemOperationLogRepository operationLogRepository;
    private final FactoryRepository factoryRepository;

    public SystemLogService(SystemImportLogRepository importLogRepository,
                            SystemOperationLogRepository operationLogRepository,
                            FactoryRepository factoryRepository) {
        this.importLogRepository = importLogRepository;
        this.operationLogRepository = operationLogRepository;
        this.factoryRepository = factoryRepository;
    }

    public Page<SystemImportLogResponse> listImportLogs(Long factoryId, String module, Pageable pageable) {
        return importLogRepository.findByFactoryIdAndModule(factoryId, module, pageable).map(this::toImportResponse);
    }

    public void recordImport(Long factoryId, String operatorName, SystemImportLogRequest request) {
        Factory factory = factoryRepository.findById(factoryId)
                .orElseThrow(() -> new IllegalArgumentException("工厂不存在"));
        SystemImportLog log = new SystemImportLog();
        log.setFactory(factory);
        log.setModule(request.getModule());
        log.setFileName(request.getFileName());
        log.setTotalCount(request.getTotalCount() != null ? request.getTotalCount() : 0);
        log.setSuccessCount(request.getSuccessCount() != null ? request.getSuccessCount() : 0);
        log.setFailCount(request.getFailCount() != null ? request.getFailCount() : 0);
        log.setStatus(request.getStatus() != null ? request.getStatus() : "成功");
        log.setOperatorName(operatorName != null ? operatorName.trim() : "系统");
        log.setErrors(request.getErrors());
        log.setCreatedAt(LocalDateTime.now());
        importLogRepository.save(log);
    }

    public Page<SystemOperationLogResponse> listOperationLogs(Long factoryId, String module, Pageable pageable) {
        return operationLogRepository.findByFactoryIdAndModule(factoryId, module, pageable).map(this::toOperationResponse);
    }

    public void recordOperation(Long factoryId, String operatorName, SystemOperationLogRequest request) {
        Factory factory = factoryRepository.findById(factoryId)
                .orElseThrow(() -> new IllegalArgumentException("工厂不存在"));
        SystemOperationLog log = new SystemOperationLog();
        log.setFactory(factory);
        log.setModule(request.getModule());
        log.setActionType(request.getActionType() != null ? request.getActionType().toUpperCase() : "OPERATE");
        log.setOperatorName(operatorName != null ? operatorName.trim() : "系统");
        log.setTargetCode(request.getTargetCode());
        log.setTargetName(request.getTargetName());
        log.setDetail(request.getDetail());
        log.setCreatedAt(LocalDateTime.now());
        operationLogRepository.save(log);
    }

    private SystemImportLogResponse toImportResponse(SystemImportLog log) {
        SystemImportLogResponse res = new SystemImportLogResponse();
        res.setId(log.getId());
        res.setModule(log.getModule());
        res.setFileName(log.getFileName());
        res.setTotalCount(log.getTotalCount());
        res.setSuccessCount(log.getSuccessCount());
        res.setFailCount(log.getFailCount());
        res.setStatus(log.getStatus());
        res.setOperatorName(log.getOperatorName());
        res.setErrors(log.getErrors());
        res.setCreatedAt(log.getCreatedAt() != null ? log.getCreatedAt().format(FMT) : null);
        return res;
    }

    private SystemOperationLogResponse toOperationResponse(SystemOperationLog log) {
        SystemOperationLogResponse res = new SystemOperationLogResponse();
        res.setId(log.getId());
        res.setModule(log.getModule());
        res.setActionType(log.getActionType());
        res.setActionLabel(actionLabel(log.getActionType()));
        res.setOperatorName(log.getOperatorName());
        res.setTargetCode(log.getTargetCode());
        res.setTargetName(log.getTargetName());
        res.setDetail(log.getDetail());
        res.setCreatedAt(log.getCreatedAt() != null ? log.getCreatedAt().format(FMT) : null);
        res.setMessage(buildMessage(log));
        return res;
    }

    private String actionLabel(String actionType) {
        if ("CREATE".equalsIgnoreCase(actionType)) return "创建";
        if ("UPDATE".equalsIgnoreCase(actionType)) return "编辑";
        if ("DELETE".equalsIgnoreCase(actionType)) return "删除";
        if ("PRINT".equalsIgnoreCase(actionType)) return "打印";
        if ("EXPORT".equalsIgnoreCase(actionType)) return "导出";
        if ("IMPORT".equalsIgnoreCase(actionType)) return "导入";
        if ("BATCH_STATUS".equalsIgnoreCase(actionType)) return "批量修改状态";
        return "操作";
    }

    private String buildMessage(SystemOperationLog log) {
        if (log.getDetail() != null && !log.getDetail().trim().isEmpty()) {
            return log.getDetail();
        }
        String label = actionLabel(log.getActionType());
        String name = log.getTargetName();
        String code = log.getTargetCode();
        if (name != null) {
            return code == null ? label + "数据【" + name + "】" : label + "数据【" + name + "】（" + code + "）";
        }
        return label + "相关数据";
    }
}

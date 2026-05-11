package com.heihu.workorder.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heihu.workorder.dto.ProcessRequest;
import com.heihu.workorder.dto.ProcessResponse;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Process;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.ProcessRepository;
import com.heihu.workorder.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProcessService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ProcessRepository processRepository;
    private final FactoryRepository factoryRepository;
    private final UserRepository userRepository;

    public ProcessService(ProcessRepository processRepository,
                          FactoryRepository factoryRepository,
                          UserRepository userRepository) {
        this.processRepository = processRepository;
        this.factoryRepository = factoryRepository;
        this.userRepository = userRepository;
    }

    private ProcessResponse toResponse(Process p) {
        ProcessResponse r = new ProcessResponse();
        r.setId(p.getId());
        r.setCode(p.getCode());
        r.setName(p.getName());
        r.setReportRatio(p.getReportRatio() != null ? p.getReportRatio() : 1);
        r.setTaskPunchIn(p.getTaskPunchIn() != null ? p.getTaskPunchIn() : false);
        r.setGoodQuantity(p.getGoodQuantity());
        r.setMachineName(p.getMachineName());
        r.setFeedCount(p.getFeedCount());
        r.setCorrespondingMachine(p.getCorrespondingMachine());
        r.setRequirements(p.getRequirements());
        r.setCreatedAt(p.getCreatedAt() != null ? p.getCreatedAt().format(FMT) : null);
        r.setCreator(p.getCreator() != null ? p.getCreator().getName() : null);

        // reportPermission: 逗号分隔 → List
        if (p.getReportPermission() != null && !p.getReportPermission().isEmpty()) {
            r.setReportPermission(Arrays.asList(p.getReportPermission().split(",")));
        } else {
            r.setReportPermission(Collections.singletonList("所有人"));
        }

        // defectItemIds: 逗号分隔 → List<Long>
        if (p.getDefectItemIds() != null && !p.getDefectItemIds().isEmpty()) {
            List<Long> ids = Arrays.stream(p.getDefectItemIds().split(","))
                    .filter(s -> !s.trim().isEmpty())
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
            r.setDefectItemIds(ids);
        } else {
            r.setDefectItemIds(Collections.emptyList());
        }

        // collectData: JSON → List
        if (p.getCollectData() != null && !p.getCollectData().isEmpty()) {
            try {
                List<ProcessResponse.CollectDataItem> items = objectMapper.readValue(
                        p.getCollectData(),
                        new TypeReference<List<ProcessResponse.CollectDataItem>>() {});
                r.setCollectData(items);
            } catch (JsonProcessingException e) {
                r.setCollectData(Collections.emptyList());
            }
        } else {
            r.setCollectData(Collections.emptyList());
        }
        return r;
    }

    public Page<ProcessResponse> list(Long factoryId, String code, String name, Pageable pageable) {
        Page<Process> page;
        boolean hasCode = code != null && !code.trim().isEmpty();
        boolean hasName = name != null && !name.trim().isEmpty();
        if (hasCode && hasName) {
            page = processRepository.findByFactoryIdAndCodeContainingAndNameContaining(factoryId, code, name, pageable);
        } else if (hasCode) {
            page = processRepository.findByFactoryIdAndCodeContaining(factoryId, code, pageable);
        } else if (hasName) {
            page = processRepository.findByFactoryIdAndNameContaining(factoryId, name, pageable);
        } else {
            page = processRepository.findByFactoryId(factoryId, pageable);
        }
        return page.map(this::toResponse);
    }

    public ProcessResponse create(Long factoryId, Long userId, ProcessRequest req) {
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("工序名称不能为空");
        }
        Factory factory = factoryRepository.findById(factoryId)
                .orElseThrow(() -> new IllegalArgumentException("工厂不存在"));

        String code = (req.getCode() != null && !req.getCode().trim().isEmpty())
                ? req.getCode().trim()
                : UUID.randomUUID().toString().substring(0, 8);
        processRepository.findByFactoryIdAndCode(factoryId, code).ifPresent(d -> {
            throw new IllegalArgumentException("工序编号已存在：" + code);
        });

        Process p = new Process();
        p.setFactory(factory);
        p.setCode(code);
        p.setName(req.getName().trim());
        p.setReportRatio(req.getReportRatio() != null ? req.getReportRatio() : 1);
        p.setTaskPunchIn(req.getTaskPunchIn() != null ? req.getTaskPunchIn() : false);
        p.setGoodQuantity(req.getGoodQuantity());
        p.setMachineName(req.getMachineName());
        p.setFeedCount(req.getFeedCount());
        p.setCorrespondingMachine(req.getCorrespondingMachine());
        p.setRequirements(req.getRequirements());
        p.setCreatedAt(LocalDateTime.now());

        // reportPermission
        if (req.getReportPermission() != null && !req.getReportPermission().isEmpty()) {
            p.setReportPermission(String.join(",", req.getReportPermission()));
        } else {
            p.setReportPermission("所有人");
        }

        // defectItemIds
        if (req.getDefectItemIds() != null && !req.getDefectItemIds().isEmpty()) {
            p.setDefectItemIds(req.getDefectItemIds().stream()
                    .map(String::valueOf).collect(Collectors.joining(",")));
        }

        // collectData
        if (req.getCollectData() != null && !req.getCollectData().isEmpty()) {
            try {
                p.setCollectData(objectMapper.writeValueAsString(req.getCollectData()));
            } catch (JsonProcessingException e) {
                p.setCollectData("[]");
            }
        }

        if (userId != null) {
            userRepository.findById(userId).ifPresent(p::setCreator);
        }

        return toResponse(processRepository.save(p));
    }

    public ProcessResponse update(Long factoryId, Long userId, Long id, ProcessRequest req) {
        Process p = processRepository.findById(id)
                .filter(x -> x.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("工序不存在"));

        if (req.getName() != null && !req.getName().trim().isEmpty()) {
            p.setName(req.getName().trim());
        }
        if (req.getCode() != null && !req.getCode().trim().isEmpty()) {
            processRepository.findByFactoryIdAndCode(factoryId, req.getCode()).ifPresent(d -> {
                if (!d.getId().equals(id)) throw new IllegalArgumentException("工序编号已存在：" + req.getCode());
            });
            p.setCode(req.getCode().trim());
        }
        if (req.getReportRatio() != null) p.setReportRatio(req.getReportRatio());
        if (req.getTaskPunchIn() != null) p.setTaskPunchIn(req.getTaskPunchIn());
        if (req.getGoodQuantity() != null) p.setGoodQuantity(req.getGoodQuantity());
        if (req.getMachineName() != null) p.setMachineName(req.getMachineName());
        if (req.getFeedCount() != null) p.setFeedCount(req.getFeedCount());
        if (req.getCorrespondingMachine() != null) p.setCorrespondingMachine(req.getCorrespondingMachine());
        if (req.getRequirements() != null) p.setRequirements(req.getRequirements());

        if (req.getReportPermission() != null) {
            p.setReportPermission(String.join(",", req.getReportPermission()));
        }
        if (req.getDefectItemIds() != null) {
            p.setDefectItemIds(req.getDefectItemIds().stream()
                    .map(String::valueOf).collect(Collectors.joining(",")));
        }
        if (req.getCollectData() != null) {
            try {
                p.setCollectData(objectMapper.writeValueAsString(req.getCollectData()));
            } catch (JsonProcessingException e) {
                p.setCollectData("[]");
            }
        }
        return toResponse(processRepository.save(p));
    }

    public void delete(Long factoryId, Long id) {
        Process p = processRepository.findById(id)
                .filter(x -> x.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("工序不存在"));
        processRepository.delete(p);
    }
}

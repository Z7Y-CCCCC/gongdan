package com.heihu.workorder.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heihu.workorder.dto.ProcessRouteRequest;
import com.heihu.workorder.dto.ProcessRouteResponse;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.ProcessRoute;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.ProcessRouteRepository;
import com.heihu.workorder.repository.ProductRepository;
import com.heihu.workorder.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ProcessRouteService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ProcessRouteRepository routeRepository;
    private final FactoryRepository factoryRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public ProcessRouteService(ProcessRouteRepository routeRepository,
                               FactoryRepository factoryRepository,
                               UserRepository userRepository,
                               ProductRepository productRepository) {
        this.routeRepository = routeRepository;
        this.factoryRepository = factoryRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    private ProcessRouteResponse toResponse(ProcessRoute r) {
        ProcessRouteResponse resp = new ProcessRouteResponse();
        resp.setId(r.getId());
        resp.setCode(r.getCode());
        resp.setName(r.getName());
        resp.setCreatedAt(r.getCreatedAt() != null ? r.getCreatedAt().format(FMT) : null);
        resp.setCreator(r.getCreator() != null ? r.getCreator().getName() : null);

        if (r.getSteps() != null && !r.getSteps().isEmpty()) {
            try {
                List<ProcessRouteResponse.RouteStepItem> steps = objectMapper.readValue(
                        r.getSteps(),
                        new TypeReference<List<ProcessRouteResponse.RouteStepItem>>() {});
                resp.setSteps(steps);
            } catch (JsonProcessingException e) {
                resp.setSteps(Collections.emptyList());
            }
        } else {
            resp.setSteps(Collections.emptyList());
        }
        return resp;
    }

    @Cacheable(cacheNames = "process.routes.list", key = "#factoryId + ':' + #code + ':' + #name + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort")
    public Page<ProcessRouteResponse> list(Long factoryId, String code, String name, Pageable pageable) {
        Page<ProcessRoute> page;
        boolean hasCode = code != null && !code.trim().isEmpty();
        boolean hasName = name != null && !name.trim().isEmpty();
        if (hasCode && hasName) {
            page = routeRepository.findByFactoryIdAndCodeContainingAndNameContaining(factoryId, code, name, pageable);
        } else if (hasCode) {
            page = routeRepository.findByFactoryIdAndCodeContaining(factoryId, code, pageable);
        } else if (hasName) {
            page = routeRepository.findByFactoryIdAndNameContaining(factoryId, name, pageable);
        } else {
            page = routeRepository.findByFactoryId(factoryId, pageable);
        }
        return page.map(this::toResponse);
    }

    @CacheEvict(cacheNames = {"process.routes.list", "products.list"}, allEntries = true)
    public ProcessRouteResponse create(Long factoryId, Long userId, ProcessRouteRequest req) {
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("工艺路线名称不能为空");
        }
        Factory factory = factoryRepository.findById(factoryId)
                .orElseThrow(() -> new IllegalArgumentException("工厂不存在"));

        String code = (req.getCode() != null && !req.getCode().trim().isEmpty())
                ? req.getCode().trim()
                : UUID.randomUUID().toString().substring(0, 8);
        routeRepository.findByFactoryIdAndCode(factoryId, code).ifPresent(d -> {
            throw new IllegalArgumentException("工艺路线编号已存在：" + code);
        });

        ProcessRoute route = new ProcessRoute();
        route.setFactory(factory);
        route.setCode(code);
        route.setName(req.getName().trim());
        route.setCreatedAt(LocalDateTime.now());

        if (req.getSteps() != null) {
            try {
                route.setSteps(objectMapper.writeValueAsString(req.getSteps()));
            } catch (JsonProcessingException e) {
                route.setSteps("[]");
            }
        }

        if (userId != null) {
            userRepository.findById(userId).ifPresent(route::setCreator);
        }

        return toResponse(routeRepository.save(route));
    }

    @CacheEvict(cacheNames = {"process.routes.list", "products.list"}, allEntries = true)
    public ProcessRouteResponse update(Long factoryId, Long userId, Long id, ProcessRouteRequest req) {
        ProcessRoute route = routeRepository.findById(id)
                .filter(r -> r.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("工艺路线不存在"));

        if (req.getName() != null && !req.getName().trim().isEmpty()) {
            route.setName(req.getName().trim());
        }
        if (req.getCode() != null && !req.getCode().trim().isEmpty()) {
            routeRepository.findByFactoryIdAndCode(factoryId, req.getCode()).ifPresent(d -> {
                if (!d.getId().equals(id)) throw new IllegalArgumentException("工艺路线编号已存在：" + req.getCode());
            });
            route.setCode(req.getCode().trim());
        }
        if (req.getSteps() != null) {
            try {
                route.setSteps(objectMapper.writeValueAsString(req.getSteps()));
            } catch (JsonProcessingException e) {
                route.setSteps("[]");
            }
        }
        return toResponse(routeRepository.save(route));
    }

    @CacheEvict(cacheNames = {"process.routes.list", "products.list"}, allEntries = true)
    public void delete(Long factoryId, Long id) {
        ProcessRoute route = routeRepository.findById(id)
                .filter(r -> r.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("工艺路线不存在"));
        if (productRepository.existsByFactoryIdAndProcessRouteId(factoryId, id)) {
            throw new IllegalArgumentException("工艺路线已被产品引用，不能删除");
        }
        routeRepository.delete(route);
    }
}

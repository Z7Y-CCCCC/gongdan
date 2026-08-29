package com.heihu.workorder.service;

import com.heihu.workorder.dto.CustomerRequest;
import com.heihu.workorder.dto.CustomerResponse;
import com.heihu.workorder.model.Customer;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.repository.CustomerRepository;
import com.heihu.workorder.repository.FactoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class CustomerService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CustomerRepository customerRepository;
    private final FactoryRepository factoryRepository;

    public CustomerService(CustomerRepository customerRepository, FactoryRepository factoryRepository) {
        this.customerRepository = customerRepository;
        this.factoryRepository = factoryRepository;
    }

    @Cacheable(cacheNames = "customers.list", key = "#factoryId + ':' + #code + ':' + #name + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort")
    public Page<CustomerResponse> list(Long factoryId, String code, String name, Pageable pageable) {
        Page<Customer> page;
        if (StringUtils.hasText(code)) {
            page = customerRepository.findByFactoryIdAndCodeContaining(factoryId, code.trim(), pageable);
        } else if (StringUtils.hasText(name)) {
            page = customerRepository.findByFactoryIdAndNameContaining(factoryId, name.trim(), pageable);
        } else {
            page = customerRepository.findByFactoryId(factoryId, pageable);
        }
        return page.map(this::toResponse);
    }

    @CacheEvict(cacheNames = "customers.list", allEntries = true)
    public CustomerResponse create(Long factoryId, CustomerRequest request) {
        requireName(request);
        String code = normalizeCode(request.getCode());
        if (!StringUtils.hasText(code)) {
            code = generateCode();
        }
        ensureUniqueCode(factoryId, code, null);
        ensureUniqueName(factoryId, request.getName().trim(), null);

        Factory factory = factoryRepository.findById(factoryId)
                .orElseThrow(() -> new IllegalArgumentException("工厂不存在"));
        LocalDateTime now = LocalDateTime.now();
        Customer customer = new Customer();
        customer.setFactory(factory);
        customer.setCode(code);
        applyRequest(customer, request);
        customer.setEnabled(request.getEnabled() == null || request.getEnabled());
        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);
        return toResponse(customerRepository.save(customer));
    }

    @CacheEvict(cacheNames = "customers.list", allEntries = true)
    public CustomerResponse update(Long factoryId, Long id, CustomerRequest request) {
        Customer customer = customerRepository.findById(id)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("客户不存在"));
        requireName(request);
        String code = normalizeCode(request.getCode());
        if (!StringUtils.hasText(code)) {
            code = customer.getCode();
        }
        ensureUniqueCode(factoryId, code, id);
        ensureUniqueName(factoryId, request.getName().trim(), id);

        customer.setCode(code);
        applyRequest(customer, request);
        if (request.getEnabled() != null) {
            customer.setEnabled(request.getEnabled());
        }
        customer.setUpdatedAt(LocalDateTime.now());
        return toResponse(customerRepository.save(customer));
    }

    @CacheEvict(cacheNames = "customers.list", allEntries = true)
    public void delete(Long factoryId, Long id) {
        Customer customer = customerRepository.findById(id)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("客户不存在"));
        customerRepository.delete(customer);
    }

    private void applyRequest(Customer customer, CustomerRequest request) {
        customer.setName(request.getName().trim());
        customer.setContactName(normalizeText(request.getContactName()));
        customer.setPhone(normalizeText(request.getPhone()));
        customer.setAddress(normalizeText(request.getAddress()));
        customer.setRemark(normalizeText(request.getRemark()));
    }

    private CustomerResponse toResponse(Customer customer) {
        CustomerResponse response = new CustomerResponse();
        response.setId(customer.getId());
        response.setCode(customer.getCode());
        response.setName(customer.getName());
        response.setContactName(customer.getContactName());
        response.setPhone(customer.getPhone());
        response.setAddress(customer.getAddress());
        response.setRemark(customer.getRemark());
        response.setEnabled(customer.isEnabled());
        response.setCreatedAt(customer.getCreatedAt() != null ? customer.getCreatedAt().format(FMT) : null);
        response.setUpdatedAt(customer.getUpdatedAt() != null ? customer.getUpdatedAt().format(FMT) : null);
        return response;
    }

    private void requireName(CustomerRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new IllegalArgumentException("客户名称不能为空");
        }
    }

    private void ensureUniqueCode(Long factoryId, String code, Long currentId) {
        customerRepository.findByFactoryIdAndCode(factoryId, code).ifPresent(found -> {
            if (currentId == null || !found.getId().equals(currentId)) {
                throw new IllegalArgumentException("客户编码已存在：" + code);
            }
        });
    }

    private void ensureUniqueName(Long factoryId, String name, Long currentId) {
        customerRepository.findByFactoryIdAndName(factoryId, name).ifPresent(found -> {
            if (currentId == null || !found.getId().equals(currentId)) {
                throw new IllegalArgumentException("客户名称已存在：" + name);
            }
        });
    }

    private String generateCode() {
        return "CUS-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : "";
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

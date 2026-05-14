package com.heihu.workorder.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heihu.workorder.dto.CustomFieldRequest;
import com.heihu.workorder.model.CustomField;
import com.heihu.workorder.model.Department;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.model.Unit;
import com.heihu.workorder.repository.CustomFieldRepository;
import com.heihu.workorder.repository.DepartmentRepository;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UnitRepository;
import com.heihu.workorder.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
public class DataInitializer implements ApplicationRunner {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final FactoryRepository factoryRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final UnitRepository unitRepository;
    private final CustomFieldRepository customFieldRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.factory-code}")
    private String factoryCode;

    @Value("${app.seed.factory-name}")
    private String factoryName;

    @Value("${app.seed.admin-username}")
    private String adminUsername;

    @Value("${app.seed.admin-password}")
    private String adminPassword;

    @Value("${app.seed.admin-name}")
    private String adminName;

    public DataInitializer(
            FactoryRepository factoryRepository,
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            UnitRepository unitRepository,
            CustomFieldRepository customFieldRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.factoryRepository = factoryRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.unitRepository = unitRepository;
        this.customFieldRepository = customFieldRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Factory factory = factoryRepository.findByCode(factoryCode)
                .orElseGet(() -> {
                    Factory item = new Factory();
                    item.setCode(factoryCode);
                    item.setName(factoryName);
                    item.setCreatedAt(LocalDateTime.now());
                    return factoryRepository.save(item);
                });

        if (!factoryName.equals(factory.getName())) {
            factory.setName(factoryName);
            factory = factoryRepository.save(factory);
        }

        seedDefaultUnits(factory);
        Factory targetFactory = factory;

        FactoryUser admin = userRepository.findByFactoryIdAndUsername(targetFactory.getId(), adminUsername)
                .orElseGet(() -> createDefaultAdmin(targetFactory));

        syncAdmin(admin);
        seedWorkOrderCustomFields(targetFactory, admin);
    }

    private FactoryUser createDefaultAdmin(Factory factory) {
        Department dept = departmentRepository.findByFactoryIdAndName(factory.getId(), "系统配置")
                .orElseGet(() -> {
                    Department item = new Department();
                    item.setFactory(factory);
                    item.setCode("SYS-CONFIG");
                    item.setName("系统配置");
                    item.setCreatedBy("System");
                    item.setCreatedAt(LocalDateTime.now());
                    return departmentRepository.save(item);
                });

        FactoryUser admin = new FactoryUser();
        admin.setFactory(factory);
        admin.setUsername(adminUsername);
        admin.setName(adminName);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        admin.setDepartment(dept);
        admin.setVirtualUser(false);
        admin.setEnabled(true);
        admin.setCreatedAt(LocalDateTime.now());
        admin.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(admin);
    }

    private void syncAdmin(FactoryUser admin) {
        boolean changed = false;
        if (!adminName.equals(admin.getName())) {
            admin.setName(adminName);
            changed = true;
        }
        if (admin.getRole() != Role.ADMIN) {
            admin.setRole(Role.ADMIN);
            changed = true;
        }
        if (!admin.isEnabled()) {
            admin.setEnabled(true);
            changed = true;
        }
        if (changed) {
            admin.setUpdatedAt(LocalDateTime.now());
            userRepository.save(admin);
        }
    }

    private void seedDefaultUnits(Factory factory) {
        for (String unitName : new String[]{"个", "件", "台"}) {
            unitRepository.findByFactoryIdAndName(factory.getId(), unitName).orElseGet(() -> {
                Unit unit = new Unit();
                unit.setFactory(factory);
                unit.setName(unitName);
                unit.setRemark("系统初始化");
                unit.setCreatedAt(LocalDateTime.now());
                return unitRepository.save(unit);
            });
        }
    }

    private void seedWorkOrderCustomFields(Factory factory, FactoryUser admin) {
        seedCustomField(factory, admin, "重量", "number", "请输入", null, Arrays.asList("0"));
        seedCustomField(factory, admin, "良品数量", "number", "请输入", null, Arrays.asList("0"));
        seedCustomField(
                factory,
                admin,
                "班次",
                "single_select",
                "请选择",
                Arrays.asList(
                        option("早班", "早班", null, true),
                        option("中班", "中班", null, false),
                        option("特殊", "特殊", null, false)
                ),
                Arrays.asList("早班")
        );
        seedCustomField(
                factory,
                admin,
                "桌号",
                "single_select",
                "请选择",
                Arrays.asList(
                        option("1号桌", "1号桌", null, false),
                        option("2号桌", "2号桌", null, false),
                        option("3号桌", "3号桌", null, false)
                ),
                Collections.emptyList()
        );
        seedCustomField(factory, admin, "颜色", "text", "请填写", null, Collections.emptyList());
        seedCustomField(
                factory,
                admin,
                "亮度",
                "single_select",
                "请选择",
                Arrays.asList(
                        option("高亮", "高亮", "#00b889", true),
                        option("标准", "标准", "#1f5eff", false),
                        option("柔和", "柔和", "#ffb84d", false)
                ),
                Arrays.asList("高亮")
        );
    }

    private void seedCustomField(
            Factory factory,
            FactoryUser admin,
            String name,
            String fieldType,
            String placeholder,
            List<CustomFieldRequest.OptionItem> options,
            List<String> defaultValues
    ) {
        customFieldRepository.findByFactoryIdAndScopeAndName(factory.getId(), "workorder", name)
                .orElseGet(() -> {
                    CustomField field = new CustomField();
                    field.setFactory(factory);
                    field.setScope("workorder");
                    field.setName(name);
                    field.setFieldType(fieldType);
                    field.setPlaceholder(placeholder);
                    field.setDisplayMode("dropdown");
                    field.setRequired(Boolean.FALSE);
                    field.setAllowUserOptions(Boolean.FALSE);
                    field.setHelperText(null);
                    field.setDescription(null);
                    field.setSortOrder(0);
                    field.setOptionsJson(writeJson(options == null ? Collections.emptyList() : options));
                    field.setDefaultValueJson(writeJson(defaultValues == null ? Collections.emptyList() : defaultValues));
                    field.setCreator(admin);
                    field.setCreatedAt(LocalDateTime.now());
                    field.setUpdatedAt(LocalDateTime.now());
                    return customFieldRepository.save(field);
                });
    }

    private CustomFieldRequest.OptionItem option(String label, String value, String color, boolean defaultSelected) {
        CustomFieldRequest.OptionItem item = new CustomFieldRequest.OptionItem();
        item.setLabel(label);
        item.setValue(value);
        item.setColor(color);
        item.setDefaultSelected(defaultSelected);
        return item;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}

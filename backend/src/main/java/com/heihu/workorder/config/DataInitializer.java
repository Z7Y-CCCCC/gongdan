package com.heihu.workorder.config;

import com.heihu.workorder.model.Department;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.repository.DepartmentRepository;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserRepository;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataInitializer implements ApplicationRunner {

    private final FactoryRepository factoryRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
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
            PasswordEncoder passwordEncoder
    ) {
        this.factoryRepository = factoryRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
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

        FactoryUser existingAdmin = userRepository.findByFactoryIdAndUsername(factory.getId(), adminUsername)
                .orElse(null);
        if (existingAdmin == null) {
            FactoryUser admin = new FactoryUser();
            final Factory finalFactory = factory;
            Department dept = departmentRepository.findByFactoryIdAndName(finalFactory.getId(), "系统配置")
                    .orElseGet(() -> {
                        Department d = new Department();
                        d.setFactory(finalFactory);
                        d.setCode("SYS-CONFIG");
                        d.setName("系统配置");
                        d.setCreatedBy("System");
                        d.setCreatedAt(LocalDateTime.now());
                        return departmentRepository.save(d);
                    });

            admin.setFactory(finalFactory);
            admin.setUsername(adminUsername);
            admin.setName(adminName);
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            admin.setRole(Role.ADMIN);
            admin.setDepartment(dept);
            admin.setVirtualUser(false);
            admin.setEnabled(true);
            admin.setCreatedAt(LocalDateTime.now());
            admin.setUpdatedAt(LocalDateTime.now());
            userRepository.save(admin);
        } else {
            boolean changed = false;
            if (!adminName.equals(existingAdmin.getName())) {
                existingAdmin.setName(adminName);
                changed = true;
            }
            if (existingAdmin.getRole() != Role.ADMIN) {
                existingAdmin.setRole(Role.ADMIN);
                changed = true;
            }
            if (!existingAdmin.isEnabled()) {
                existingAdmin.setEnabled(true);
                changed = true;
            }
            if (changed) {
                existingAdmin.setUpdatedAt(LocalDateTime.now());
                userRepository.save(existingAdmin);
            }
        }
    }
}

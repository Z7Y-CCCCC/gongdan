package com.heihu.workorder.config;

import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.repository.CustomFieldRepository;
import com.heihu.workorder.repository.DepartmentRepository;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UnitRepository;
import com.heihu.workorder.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DataInitializerTest {

    private final FactoryRepository factories = mock(FactoryRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final DataInitializer initializer = new DataInitializer(factories, users,
            mock(DepartmentRepository.class), mock(UnitRepository.class),
            mock(CustomFieldRepository.class), mock(PasswordEncoder.class));
    private Factory factory;
    private Factory superFactory;
    private FactoryUser admin;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(initializer, "factoryCode", "FACTORY");
        ReflectionTestUtils.setField(initializer, "factoryName", "Seed factory");
        ReflectionTestUtils.setField(initializer, "adminUsername", "admin");
        ReflectionTestUtils.setField(initializer, "adminPassword", "seed-password");
        ReflectionTestUtils.setField(initializer, "adminName", "Seed admin");
        ReflectionTestUtils.setField(initializer, "superAdminFactoryCode", "ADMIN");
        ReflectionTestUtils.setField(initializer, "superAdminFactoryName", "Super factory");
        ReflectionTestUtils.setField(initializer, "superAdminUsername", "admin");
        ReflectionTestUtils.setField(initializer, "superAdminPassword", "super-password");
        ReflectionTestUtils.setField(initializer, "superAdminName", "Super admin");
        factory = new Factory();
        factory.setId(1L);
        factory.setCode("FACTORY");
        superFactory = new Factory();
        superFactory.setId(2L);
        superFactory.setCode("ADMIN");
        admin = new FactoryUser();
        admin.setId(10L);
        admin.setFactory(factory);
        admin.setName("Persisted name");
        admin.setRole(Role.PRODUCTION);
        admin.setEnabled(false);
        admin.setPasswordHash("persisted-password-hash");
        FactoryUser superAdmin = new FactoryUser();
        superAdmin.setId(20L);
        superAdmin.setFactory(superFactory);
        when(factories.findByCode("FACTORY")).thenReturn(Optional.of(factory));
        when(factories.findByCode("ADMIN")).thenReturn(Optional.of(superFactory));
        when(users.findByFactoryIdAndUsername(1L, "admin")).thenReturn(Optional.of(admin));
        when(users.findByFactoryIdAndUsername(2L, "admin")).thenReturn(Optional.of(superAdmin));
    }

    @Test
    void restartDoesNotRestoreDisabledAccountOrRevokedAdministratorPermissions() {
        initializer.run(null);
        assertThat(admin.isEnabled()).isFalse();
        assertThat(admin.getRole()).isEqualTo(Role.PRODUCTION);
        assertThat(admin.getName()).isEqualTo("Persisted name");
        assertThat(admin.getPasswordHash()).isEqualTo("persisted-password-hash");
        verify(users, never()).save(any());
    }

    @Test
    void restartDoesNotReactivateExistingDisabledFactories() {
        factory.setEnabled(false);
        superFactory.setEnabled(false);
        initializer.run(null);
        assertThat(factory.isEnabled()).isFalse();
        assertThat(superFactory.isEnabled()).isFalse();
        verify(factories, never()).save(any());
    }
}

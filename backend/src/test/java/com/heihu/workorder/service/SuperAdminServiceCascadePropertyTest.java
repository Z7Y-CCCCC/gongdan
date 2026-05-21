package com.heihu.workorder.service;

import com.heihu.workorder.dto.FactoryListItem;
import com.heihu.workorder.dto.ManagementListResponse;
import com.heihu.workorder.model.AuthorizationCode;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.repository.AuthorizationCodeRepository;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserRepository;
import net.jqwik.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property tests for SuperAdminService cascading disable/enable and management list.
 *
 * Feature: super-admin-management
 * Validates: Requirements 6.1, 7.1, 7.8, 7.9
 */
class SuperAdminServiceCascadePropertyTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private SuperAdminService createService(
            FactoryRepository factoryRepo,
            UserRepository userRepo,
            AuthorizationCodeRepository authCodeRepo
    ) {
        return new SuperAdminService(factoryRepo, userRepo, authCodeRepo, passwordEncoder);
    }

    // ─── Property 12: Management list excludes admin factory and sorts descending ─
    // For any set of factories in the system, the factory list returned by the
    // management endpoint SHALL exclude the factory with code "admin" and SHALL be
    // sorted by createdAt in descending order.
    // **Validates: Requirements 6.1**
    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 12: Management list excludes admin factory and sorts descending")
    void managementListExcludesAdminAndSortsDescending(
            @ForAll("factoryListsWithAdmin") List<Factory> allFactories
    ) {
        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        AuthorizationCodeRepository authCodeRepo = mock(AuthorizationCodeRepository.class);
        SuperAdminService service = createService(factoryRepo, userRepo, authCodeRepo);

        // Separate admin factory from non-admin factories
        Factory adminFactory = allFactories.stream()
                .filter(f -> "admin".equals(f.getCode()))
                .findFirst()
                .orElse(null);

        // The repository method findByCodeNotOrderByCreatedAtDesc("admin") returns
        // non-admin factories sorted by createdAt desc
        List<Factory> nonAdminFactories = allFactories.stream()
                .filter(f -> !"admin".equals(f.getCode()))
                .sorted(Comparator.comparing(Factory::getCreatedAt).reversed())
                .collect(Collectors.toList());

        when(factoryRepo.findByCodeNotOrderByCreatedAtDesc("admin")).thenReturn(nonAdminFactories);
        when(factoryRepo.findByCode("admin")).thenReturn(Optional.ofNullable(adminFactory));

        // Mock empty user and auth code lists for simplicity
        Long adminFactoryId = adminFactory != null ? adminFactory.getId() : -1L;
        when(userRepo.findByRoleAndVirtualUserFalseAndFactoryIdNotOrderByCreatedAtDesc(Role.ADMIN, adminFactoryId))
                .thenReturn(Collections.emptyList());
        when(authCodeRepo.findAllByOrderByCreatedAtDesc()).thenReturn(Collections.emptyList());

        // Act
        ManagementListResponse response = service.getManagementList();

        // Assert
        List<FactoryListItem> factoryItems = response.getFactories();

        // 1. Admin factory should NOT be in the list
        assertThat(factoryItems)
                .noneMatch(item -> "admin".equals(item.getFactoryCode()));

        // 2. All non-admin factories should be present
        assertThat(factoryItems).hasSize(nonAdminFactories.size());

        // 3. Should be sorted by createdAt in descending order
        for (int i = 0; i < factoryItems.size() - 1; i++) {
            assertThat(factoryItems.get(i).getCreatedAt())
                    .isAfterOrEqualTo(factoryItems.get(i + 1).getCreatedAt());
        }
    }

    // ─── Property 13: Cascading factory disable ──────────────────────────────────
    // For any factory (not the admin factory) with N users and M active authorization
    // codes, disabling the factory SHALL result in: factory.enabled=false, all N users
    // having enabled=false, and all M active codes having disabled=true.
    // **Validates: Requirements 7.1**
    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 13: Cascading factory disable")
    void cascadingFactoryDisable(
            @ForAll("validNonAdminFactoryCodes") String factoryCode,
            @ForAll("userCounts") int userCount,
            @ForAll("authCodeCounts") int activeCodeCount
    ) {
        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        AuthorizationCodeRepository authCodeRepo = mock(AuthorizationCodeRepository.class);
        SuperAdminService service = createService(factoryRepo, userRepo, authCodeRepo);

        // Create an enabled factory
        Factory factory = new Factory();
        factory.setId(1L);
        factory.setCode(factoryCode);
        factory.setName("Test Factory");
        factory.setEnabled(true);
        factory.setCreatedAt(LocalDateTime.now().minusDays(10));

        when(factoryRepo.findByCode(factoryCode)).thenReturn(Optional.of(factory));
        when(factoryRepo.save(any(Factory.class))).thenAnswer(inv -> inv.getArgument(0));

        // Create N users (mix of enabled and disabled)
        List<FactoryUser> users = IntStream.range(0, userCount)
                .mapToObj(i -> {
                    FactoryUser user = new FactoryUser();
                    user.setId((long) (i + 1));
                    user.setFactory(factory);
                    user.setUsername("user" + i);
                    user.setName("User " + i);
                    user.setPasswordHash("hash");
                    user.setRole(Role.ADMIN);
                    user.setEnabled(true);
                    user.setVirtualUser(false);
                    user.setCreatedAt(LocalDateTime.now());
                    user.setUpdatedAt(LocalDateTime.now());
                    return user;
                })
                .collect(Collectors.toList());

        when(userRepo.findByFactoryId(factory.getId())).thenReturn(users);
        when(userRepo.save(any(FactoryUser.class))).thenAnswer(inv -> inv.getArgument(0));

        // Create M active authorization codes (not consumed, not disabled)
        List<AuthorizationCode> activeCodes = IntStream.range(0, activeCodeCount)
                .mapToObj(i -> {
                    AuthorizationCode code = new AuthorizationCode();
                    code.setId((long) (i + 1));
                    code.setCode("CODE" + String.format("%04d", i));
                    code.setFactory(factory);
                    code.setCreatedAt(LocalDateTime.now());
                    code.setExpiresAt(LocalDateTime.now().plusMinutes(30));
                    code.setConsumed(false);
                    code.setDisabled(false);
                    return code;
                })
                .collect(Collectors.toList());

        when(authCodeRepo.findByFactoryIdAndConsumedFalseAndDisabledFalse(factory.getId()))
                .thenReturn(activeCodes);
        when(authCodeRepo.save(any(AuthorizationCode.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        service.disableFactory(factoryCode);

        // Assert
        // 1. Factory should be disabled
        assertThat(factory.isEnabled()).isFalse();
        verify(factoryRepo).save(factory);

        // 2. All users should be disabled
        for (FactoryUser user : users) {
            assertThat(user.isEnabled()).isFalse();
        }
        verify(userRepo, times(userCount)).save(any(FactoryUser.class));

        // 3. All active codes should be disabled
        for (AuthorizationCode code : activeCodes) {
            assertThat(code.isDisabled()).isTrue();
            assertThat(code.getDisabledAt()).isNotNull();
        }
        verify(authCodeRepo, times(activeCodeCount)).save(any(AuthorizationCode.class));
    }

    // ─── Property 15: Factory enable does not cascade to child resources ─────────
    // For any disabled factory with disabled users and disabled authorization codes,
    // enabling the factory SHALL only set factory.enabled=true without modifying the
    // enabled/disabled state of any users or authorization codes.
    // **Validates: Requirements 7.8**
    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 15: Factory enable does not cascade to child resources")
    void factoryEnableDoesNotCascade(
            @ForAll("validNonAdminFactoryCodes") String factoryCode,
            @ForAll("userCounts") int userCount,
            @ForAll("authCodeCounts") int codeCount
    ) {
        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        AuthorizationCodeRepository authCodeRepo = mock(AuthorizationCodeRepository.class);
        SuperAdminService service = createService(factoryRepo, userRepo, authCodeRepo);

        // Create a disabled factory
        Factory factory = new Factory();
        factory.setId(1L);
        factory.setCode(factoryCode);
        factory.setName("Disabled Factory");
        factory.setEnabled(false);
        factory.setCreatedAt(LocalDateTime.now().minusDays(10));

        when(factoryRepo.findByCode(factoryCode)).thenReturn(Optional.of(factory));
        when(factoryRepo.save(any(Factory.class))).thenAnswer(inv -> inv.getArgument(0));

        // Create disabled users
        List<FactoryUser> disabledUsers = IntStream.range(0, userCount)
                .mapToObj(i -> {
                    FactoryUser user = new FactoryUser();
                    user.setId((long) (i + 1));
                    user.setFactory(factory);
                    user.setUsername("user" + i);
                    user.setName("User " + i);
                    user.setPasswordHash("hash");
                    user.setRole(Role.ADMIN);
                    user.setEnabled(false); // disabled
                    user.setVirtualUser(false);
                    user.setCreatedAt(LocalDateTime.now());
                    user.setUpdatedAt(LocalDateTime.now());
                    return user;
                })
                .collect(Collectors.toList());

        // Create disabled authorization codes
        List<AuthorizationCode> disabledCodes = IntStream.range(0, codeCount)
                .mapToObj(i -> {
                    AuthorizationCode code = new AuthorizationCode();
                    code.setId((long) (i + 1));
                    code.setCode("CODE" + String.format("%04d", i));
                    code.setFactory(factory);
                    code.setCreatedAt(LocalDateTime.now());
                    code.setExpiresAt(LocalDateTime.now().plusMinutes(30));
                    code.setConsumed(false);
                    code.setDisabled(true); // disabled
                    code.setDisabledAt(LocalDateTime.now().minusHours(1));
                    return code;
                })
                .collect(Collectors.toList());

        // Act
        service.enableFactory(factoryCode);

        // Assert
        // 1. Factory should be enabled
        assertThat(factory.isEnabled()).isTrue();
        verify(factoryRepo).save(factory);

        // 2. Users should NOT be modified (no save calls to userRepo)
        verify(userRepo, never()).save(any(FactoryUser.class));
        verify(userRepo, never()).findByFactoryId(anyLong());

        // 3. Auth codes should NOT be modified (no save calls to authCodeRepo)
        verify(authCodeRepo, never()).save(any(AuthorizationCode.class));

        // 4. Verify users remain disabled
        for (FactoryUser user : disabledUsers) {
            assertThat(user.isEnabled()).isFalse();
        }

        // 5. Verify auth codes remain disabled
        for (AuthorizationCode code : disabledCodes) {
            assertThat(code.isDisabled()).isTrue();
        }
    }

    // ─── Property 16: Disable operation idempotence ──────────────────────────────
    // For any resource (factory, user, or authorization code) that is already disabled,
    // a subsequent disable request SHALL return success without modifying the resource's
    // state or timestamps.
    // **Validates: Requirements 7.9**
    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 16: Disable operation idempotence - factory")
    void disableAlreadyDisabledFactoryIsIdempotent(
            @ForAll("validNonAdminFactoryCodes") String factoryCode
    ) {
        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        AuthorizationCodeRepository authCodeRepo = mock(AuthorizationCodeRepository.class);
        SuperAdminService service = createService(factoryRepo, userRepo, authCodeRepo);

        // Create an already-disabled factory
        Factory factory = new Factory();
        factory.setId(1L);
        factory.setCode(factoryCode);
        factory.setName("Already Disabled Factory");
        factory.setEnabled(false); // already disabled
        factory.setCreatedAt(LocalDateTime.now().minusDays(10));

        when(factoryRepo.findByCode(factoryCode)).thenReturn(Optional.of(factory));

        // Act - should succeed without error
        service.disableFactory(factoryCode);

        // Assert
        // 1. Factory should remain disabled
        assertThat(factory.isEnabled()).isFalse();

        // 2. No save should be called (state not modified)
        verify(factoryRepo, never()).save(any(Factory.class));

        // 3. No cascading operations should occur
        verify(userRepo, never()).findByFactoryId(anyLong());
        verify(userRepo, never()).save(any(FactoryUser.class));
        verify(authCodeRepo, never()).findByFactoryIdAndConsumedFalseAndDisabledFalse(anyLong());
        verify(authCodeRepo, never()).save(any(AuthorizationCode.class));
    }

    // ─── Providers ────────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> validNonAdminFactoryCodes() {
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(1)
                .ofMaxLength(16)
                .filter(code -> !"ADMIN".equalsIgnoreCase(code));
    }

    @Provide
    Arbitrary<Integer> userCounts() {
        return Arbitraries.integers().between(0, 10);
    }

    @Provide
    Arbitrary<Integer> authCodeCounts() {
        return Arbitraries.integers().between(0, 10);
    }

    @Provide
    Arbitrary<List<Factory>> factoryListsWithAdmin() {
        // Generate a list of factories that always includes the admin factory
        Arbitrary<Integer> countArb = Arbitraries.integers().between(0, 8);

        return countArb.flatMap(count -> {
            // Create admin factory
            Factory adminFactory = new Factory();
            adminFactory.setId(1L);
            adminFactory.setCode("admin");
            adminFactory.setName("Admin Factory");
            adminFactory.setEnabled(true);
            adminFactory.setCreatedAt(LocalDateTime.of(2020, 1, 1, 0, 0));

            // Generate non-admin factories with distinct timestamps
            return Arbitraries.just(count).map(n -> {
                List<Factory> factories = new ArrayList<>();
                factories.add(adminFactory);

                for (int i = 0; i < n; i++) {
                    Factory f = new Factory();
                    f.setId((long) (i + 2));
                    f.setCode("F" + String.format("%03d", i));
                    f.setName("Factory " + i);
                    f.setEnabled(i % 2 == 0);
                    // Use distinct timestamps to ensure deterministic sorting
                    f.setCreatedAt(LocalDateTime.of(2024, 1, 1, 0, 0).plusDays(i));
                    factories.add(f);
                }

                return factories;
            });
        });
    }
}

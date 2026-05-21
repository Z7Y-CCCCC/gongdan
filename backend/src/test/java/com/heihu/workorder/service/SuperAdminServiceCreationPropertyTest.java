package com.heihu.workorder.service;

import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.repository.AuthorizationCodeRepository;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserRepository;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property tests for SuperAdminService super admin creation.
 *
 * Feature: super-admin-management
 * Validates: Requirements 8.1, 8.5, 8.7
 */
class SuperAdminServiceCreationPropertyTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private SuperAdminService createService(FactoryRepository factoryRepo, UserRepository userRepo) {
        AuthorizationCodeRepository authCodeRepo = mock(AuthorizationCodeRepository.class);
        return new SuperAdminService(factoryRepo, userRepo, authCodeRepo, passwordEncoder);
    }

    private Factory createAdminFactory() {
        Factory factory = new Factory();
        factory.setId(1L);
        factory.setCode("admin");
        factory.setName("Admin Factory");
        factory.setEnabled(true);
        factory.setCreatedAt(LocalDateTime.now());
        return factory;
    }

    // ─── Property 17: Super admin creation with valid inputs ──────────────────────
    // For any username matching [A-Za-z0-9]{3,32} that does not already exist in
    // the admin factory, and for any password of 6-64 characters, the
    // SuperAdminService SHALL create a FactoryUser in the admin factory with role
    // ADMIN, enabled=true, and virtualUser=false.
    // **Validates: Requirements 8.1, 8.5, 8.7**
    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 17: Super admin creation with valid inputs")
    void superAdminCreationWithValidInputs(
            @ForAll("validSuperAdminUsernames") String username,
            @ForAll("validSuperAdminPasswords") String password
    ) {
        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        SuperAdminService service = createService(factoryRepo, userRepo);

        Factory adminFactory = createAdminFactory();
        when(factoryRepo.findByCode("admin")).thenReturn(Optional.of(adminFactory));
        when(userRepo.findByFactoryIdAndUsername(adminFactory.getId(), username)).thenReturn(Optional.empty());
        when(userRepo.save(any(FactoryUser.class))).thenAnswer(invocation -> {
            FactoryUser u = invocation.getArgument(0);
            u.setId(100L);
            return u;
        });

        // Act
        service.createSuperAdmin(username, password);

        // Assert - verify a FactoryUser was saved with correct properties
        ArgumentCaptor<FactoryUser> captor = ArgumentCaptor.forClass(FactoryUser.class);
        verify(userRepo).save(captor.capture());

        FactoryUser savedUser = captor.getValue();
        assertThat(savedUser.getFactory()).isEqualTo(adminFactory);
        assertThat(savedUser.getUsername()).isEqualTo(username);
        assertThat(savedUser.getName()).isEqualTo(username);
        assertThat(savedUser.getRole()).isEqualTo(Role.ADMIN);
        assertThat(savedUser.isEnabled()).isTrue();
        assertThat(savedUser.isVirtualUser()).isFalse();
        // Verify password was encoded (not stored as plaintext)
        assertThat(savedUser.getPasswordHash()).isNotEqualTo(password);
        assertThat(passwordEncoder.matches(password, savedUser.getPasswordHash())).isTrue();
    }

    // ─── Property 18: Super admin input validation ────────────────────────────────
    // For any username that does not match [A-Za-z0-9]{3,32} (too short, too long,
    // or contains non-alphanumeric characters), OR for any password shorter than 6
    // or longer than 64 characters, the super admin creation request SHALL be
    // rejected with a validation error.
    // **Validates: Requirements 8.5, 8.7**
    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 18: Super admin input validation - invalid username too short")
    void invalidUsernameTooShortIsRejected(
            @ForAll("tooShortUsernames") String username,
            @ForAll("validSuperAdminPasswords") String password
    ) {
        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        SuperAdminService service = createService(factoryRepo, userRepo);

        // Act & Assert
        ApiException exception = catchThrowableOfType(
                () -> service.createSuperAdmin(username, password),
                ApiException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).isEqualTo("用户名格式无效");
        verify(userRepo, never()).save(any(FactoryUser.class));
    }

    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 18: Super admin input validation - invalid username too long")
    void invalidUsernameTooLongIsRejected(
            @ForAll("tooLongUsernames") String username,
            @ForAll("validSuperAdminPasswords") String password
    ) {
        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        SuperAdminService service = createService(factoryRepo, userRepo);

        // Act & Assert
        ApiException exception = catchThrowableOfType(
                () -> service.createSuperAdmin(username, password),
                ApiException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).isEqualTo("用户名格式无效");
        verify(userRepo, never()).save(any(FactoryUser.class));
    }

    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 18: Super admin input validation - invalid username with non-alphanumeric chars")
    void invalidUsernameWithSpecialCharsIsRejected(
            @ForAll("usernamesWithSpecialChars") String username,
            @ForAll("validSuperAdminPasswords") String password
    ) {
        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        SuperAdminService service = createService(factoryRepo, userRepo);

        // Act & Assert
        ApiException exception = catchThrowableOfType(
                () -> service.createSuperAdmin(username, password),
                ApiException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).isEqualTo("用户名格式无效");
        verify(userRepo, never()).save(any(FactoryUser.class));
    }

    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 18: Super admin input validation - password too short")
    void passwordTooShortIsRejected(
            @ForAll("validSuperAdminUsernames") String username,
            @ForAll("tooShortPasswords") String password
    ) {
        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        SuperAdminService service = createService(factoryRepo, userRepo);

        Factory adminFactory = createAdminFactory();
        when(factoryRepo.findByCode("admin")).thenReturn(Optional.of(adminFactory));
        when(userRepo.findByFactoryIdAndUsername(adminFactory.getId(), username)).thenReturn(Optional.empty());

        // Act & Assert
        ApiException exception = catchThrowableOfType(
                () -> service.createSuperAdmin(username, password),
                ApiException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).isEqualTo("密码长度不能少于6位");
        verify(userRepo, never()).save(any(FactoryUser.class));
    }

    @Property(tries = 100)
    @Label("Feature: super-admin-management, Property 18: Super admin input validation - password too long")
    void passwordTooLongIsRejected(
            @ForAll("validSuperAdminUsernames") String username,
            @ForAll("tooLongPasswords") String password
    ) {
        // Arrange
        FactoryRepository factoryRepo = mock(FactoryRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        SuperAdminService service = createService(factoryRepo, userRepo);

        Factory adminFactory = createAdminFactory();
        when(factoryRepo.findByCode("admin")).thenReturn(Optional.of(adminFactory));
        when(userRepo.findByFactoryIdAndUsername(adminFactory.getId(), username)).thenReturn(Optional.empty());

        // Act & Assert
        ApiException exception = catchThrowableOfType(
                () -> service.createSuperAdmin(username, password),
                ApiException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).isEqualTo("密码长度不能超过64位");
        verify(userRepo, never()).save(any(FactoryUser.class));
    }

    // ─── Providers ────────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> validSuperAdminUsernames() {
        // Valid usernames: [A-Za-z0-9]{3,32}
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .ofMinLength(3)
                .ofMaxLength(32);
    }

    @Provide
    Arbitrary<String> validSuperAdminPasswords() {
        // Valid passwords: 6-64 characters (any printable chars)
        return Arbitraries.strings()
                .withCharRange('!', '~')
                .ofMinLength(6)
                .ofMaxLength(64);
    }

    @Provide
    Arbitrary<String> tooShortUsernames() {
        // Usernames with 1-2 alphanumeric characters (too short)
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .ofMinLength(1)
                .ofMaxLength(2);
    }

    @Provide
    Arbitrary<String> tooLongUsernames() {
        // Usernames with 33+ alphanumeric characters (too long)
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .ofMinLength(33)
                .ofMaxLength(50);
    }

    @Provide
    Arbitrary<String> usernamesWithSpecialChars() {
        // Usernames of valid length (3-32) but containing non-alphanumeric characters
        return Arbitraries.strings()
                .withCharRange('!', '/')  // special chars before digits
                .withCharRange(':', '@')  // special chars between digits and uppercase
                .withCharRange('[', '`')  // special chars between uppercase and lowercase
                .withCharRange('{', '~')  // special chars after lowercase
                .ofMinLength(3)
                .ofMaxLength(32);
    }

    @Provide
    Arbitrary<String> tooShortPasswords() {
        // Passwords with 1-5 characters (too short)
        return Arbitraries.strings()
                .withCharRange('!', '~')
                .ofMinLength(1)
                .ofMaxLength(5);
    }

    @Provide
    Arbitrary<String> tooLongPasswords() {
        // Passwords with 65+ characters (too long)
        return Arbitraries.strings()
                .withCharRange('!', '~')
                .ofMinLength(65)
                .ofMaxLength(80);
    }
}

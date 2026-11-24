package com.heihu.workorder.service;

import com.heihu.workorder.dto.CreateUserRequest;
import com.heihu.workorder.dto.UpdateUserRequest;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.Department;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.repository.DepartmentRepository;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.repository.UserSessionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserServiceBoundaryTest {

    private final FactoryRepository factories = mock(FactoryRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final UserSessionRepository sessions = mock(UserSessionRepository.class);
    private final UserService service = new UserService(factories, users, departments, encoder, sessions);
    private Factory factory;
    private FactoryUser user;
    private Department department;

    @BeforeEach
    void setUp() {
        factory = new Factory();
        factory.setId(1L);
        factory.setEnabled(true);
        user = new FactoryUser();
        user.setId(10L);
        user.setFactory(factory);
        user.setRole(Role.PRODUCTION);
        department = new Department();
        department.setId(5L);
        department.setFactory(factory);
        department.setName("Assembly");
        when(factories.findById(1L)).thenReturn(Optional.of(factory));
        when(users.findByIdAndFactoryId(10L, 1L)).thenReturn(Optional.of(user));
        when(departments.findById(5L)).thenReturn(Optional.of(department));
        when(users.save(any(FactoryUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(encoder.encode(anyString())).thenReturn("encoded");
    }

    @Test
    void creatingUserRejectsAnotherFactoryDepartment() {
        Factory other = new Factory();
        other.setId(2L);
        department.setFactory(other);
        assertThatThrownBy(() -> service.createUser(1L, createRequest()))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(users, never()).save(any());
    }

    @Test
    void updatingUserRejectsAnotherFactoryDepartment() {
        Factory other = new Factory();
        other.setId(2L);
        department.setFactory(other);
        assertThatThrownBy(() -> service.updateUser(1L, 99L, 10L, updateRequest()))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(users, never()).save(any());
    }

    @Test
    void missingDepartmentIsNotSilentlyCleared() {
        when(departments.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createUser(1L, createRequest())).isInstanceOf(ApiException.class);
        verify(users, never()).save(any());
    }

    @Test
    void sameFactoryDepartmentCanBeAssigned() {
        assertThat(service.createUser(1L, createRequest()).getDepartmentId()).isEqualTo(5L);
        assertThat(service.updateUser(1L, 99L, 10L, updateRequest()).getDepartmentName()).isEqualTo("Assembly");
    }

    @Test
    void resettingPasswordRevokesExistingSessions() {
        service.resetPassword(1L, 10L, "new-password");
        verify(sessions).kickActiveSessions(10L);
    }

    @Test
    void disablingUserRevokesExistingSessions() {
        service.updateStatus(1L, 99L, 10L, false);
        verify(sessions).kickActiveSessions(10L);
    }

    @Test
    void unknownImportedRoleDoesNotCreateAnAdministrator() {
        UserService.ImportResult result = service.importUsersCsv(1L,
                "账号,姓名,角色,手机号,部门,密码\nworker,Worker,unexpected,,,password123");
        assertThat(result.getSuccess()).isZero();
        assertThat(result.getFail()).isEqualTo(1);
        verify(users, never()).save(any());
    }

    @Test
    void exportedQuotedMultilineFieldsCanBeImportedAsOneRecord() {
        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder bcrypt =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4);
        user.setUsername("worker");
        user.setName("First,\n\"Second\"\rThird");
        user.setPasswordHash(bcrypt.encode("password123"));
        user.setEnabled(true);
        when(users.findByFactoryIdOrderByCreatedAtAsc(1L)).thenReturn(java.util.Collections.singletonList(user));

        UserService.ImportResult result = service.importUsersCsv(1L, service.exportUsersCsv(1L));

        assertThat(result.getSuccess()).isEqualTo(1);
        assertThat(result.getFail()).isZero();
        assertThat(result.getTotal()).isEqualTo(1);
        org.mockito.ArgumentCaptor<FactoryUser> saved = org.mockito.ArgumentCaptor.forClass(FactoryUser.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo(user.getName());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo(user.getPasswordHash());
        assertThat(bcrypt.matches("password123", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void supportedBcryptHashVersionsRemainUsableAfterImport() {
        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder bcrypt =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4);
        String encoded = bcrypt.encode("password123");
        for (String version : new String[]{"2a", "2b", "2y"}) {
            UserService.ImportResult result = service.importUsersCsv(1L,
                    "账号,姓名,角色,手机号,部门,密码\nworker-" + version + ",Worker,PRODUCTION,,,$"
                            + version + encoded.substring(3));
            assertThat(result.getSuccess()).isEqualTo(1);
            assertThat(result.getFail()).isZero();
        }
        org.mockito.ArgumentCaptor<FactoryUser> saved = org.mockito.ArgumentCaptor.forClass(FactoryUser.class);
        verify(users, times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(value ->
                assertThat(bcrypt.matches("password123", value.getPasswordHash())).isTrue());
    }

    @Test
    void malformedCsvIsRejectedBeforeAnyEarlierRecordIsSaved() {
        assertThatThrownBy(() -> service.importUsersCsv(1L,
                "账号,姓名,角色,手机号,部门,密码\nworker,Worker,PRODUCTION,,,password123\nnext,\"unclosed,PRODUCTION,,,password123"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(users, never()).save(any());
    }

    @Test
    void invalidImportedPasswordHashAndOversizedFieldsAreSkippedBeforePersistence() {
        UserService.ImportResult result = service.importUsersCsv(1L,
                "账号,姓名,角色,手机号,部门,密码\nworker,Worker,PRODUCTION,,,$2-not-a-hash\n"
                        + new String(new char[65]).replace('\0', 'x') + ",Worker,PRODUCTION,,,password123");
        assertThat(result.getSuccess()).isZero();
        assertThat(result.getFail()).isEqualTo(2);
        verify(users, never()).save(any());
    }

    @Test
    void importedUnknownDepartmentIsNotSilentlyDiscarded() {
        UserService.ImportResult result = service.importUsersCsv(1L,
                "账号,姓名,角色,手机号,部门,密码\nworker,Worker,PRODUCTION,,missing,password123");
        assertThat(result.getSuccess()).isZero();
        assertThat(result.getFail()).isEqualTo(1);
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0)).contains("部门不存在");
        verify(users, never()).save(any());
    }

    private CreateUserRequest createRequest() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("worker");
        request.setName("Worker");
        request.setPassword("password123");
        request.setRole(Role.PRODUCTION);
        request.setDepartmentId(5L);
        return request;
    }

    private UpdateUserRequest updateRequest() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setUsername("worker");
        request.setName("Worker");
        request.setRole(Role.PRODUCTION);
        request.setDepartmentId(5L);
        return request;
    }
}

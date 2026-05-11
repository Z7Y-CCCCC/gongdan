package com.heihu.workorder.service;

import com.heihu.workorder.dto.CreateUserRequest;
import com.heihu.workorder.dto.ResetPasswordResponse;
import com.heihu.workorder.dto.UpdateUserRequest;
import com.heihu.workorder.dto.UserResponse;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.Role;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.repository.DepartmentRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserService {

    private static final String DEFAULT_RESET_PASSWORD = "123456";

    private final FactoryRepository factoryRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
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

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(Long factoryId) {
        return userRepository.findByFactoryIdOrderByCreatedAtAsc(factoryId)
                .stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserResponse createUser(Long factoryId, CreateUserRequest request) {
        Factory factory = factoryRepository.findById(factoryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "工厂不存在"));

        String username = request.getUsername().trim();
        String phone = normalizeNullable(request.getPhone());
        if (userRepository.findByFactoryIdAndUsername(factoryId, username).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "该账号已存在");
        }
        if (StringUtils.hasText(phone) && userRepository.findByFactoryIdAndPhone(factoryId, phone).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "该手机号已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        FactoryUser user = new FactoryUser();
        user.setFactory(factory);
        user.setUsername(username);
        user.setPhone(phone);
        user.setName(request.getName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        if (request.getDepartmentId() != null) {
            user.setDepartment(departmentRepository.findById(request.getDepartmentId()).orElse(null));
        }
        user.setVirtualUser(Boolean.TRUE.equals(request.getVirtualUser()));
        user.setEnabled(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateUser(Long factoryId, Long operatorUserId, Long userId, UpdateUserRequest request) {
        FactoryUser user = getFactoryUser(factoryId, userId);
        ensureEditableRole(operatorUserId, userId, request.getRole());
        applyUniqueAccountFields(factoryId, userId, request.getUsername(), request.getPhone());

        user.setUsername(request.getUsername().trim());
        user.setPhone(normalizeNullable(request.getPhone()));
        user.setName(request.getName().trim());
        user.setRole(request.getRole());
        if (request.getDepartmentId() != null) {
            user.setDepartment(departmentRepository.findById(request.getDepartmentId()).orElse(null));
        } else {
            user.setDepartment(null);
        }
        user.setVirtualUser(Boolean.TRUE.equals(request.getVirtualUser()));
        user.setUpdatedAt(LocalDateTime.now());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public ResetPasswordResponse resetPassword(Long factoryId, Long userId) {
        FactoryUser user = getFactoryUser(factoryId, userId);
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_RESET_PASSWORD));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return new ResetPasswordResponse("密码已重置", DEFAULT_RESET_PASSWORD);
    }

    @Transactional
    public UserResponse unbindPhone(Long factoryId, Long userId) {
        FactoryUser user = getFactoryUser(factoryId, userId);
        user.setPhone(null);
        user.setUpdatedAt(LocalDateTime.now());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateStatus(Long factoryId, Long operatorUserId, Long userId, Boolean enabled) {
        if (operatorUserId.equals(userId) && !Boolean.TRUE.equals(enabled)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不能停用当前登录账号");
        }
        FactoryUser user = getFactoryUser(factoryId, userId);
        user.setEnabled(Boolean.TRUE.equals(enabled));
        user.setUpdatedAt(LocalDateTime.now());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse updatePermission(Long factoryId, Long operatorUserId, Long userId, Role role) {
        ensureEditableRole(operatorUserId, userId, role);
        FactoryUser user = getFactoryUser(factoryId, userId);
        user.setRole(role);
        user.setUpdatedAt(LocalDateTime.now());
        return UserResponse.from(userRepository.save(user));
    }

    private FactoryUser getFactoryUser(Long factoryId, Long userId) {
        return userRepository.findByIdAndFactoryId(userId, factoryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "用户不存在"));
    }

    private void applyUniqueAccountFields(Long factoryId, Long userId, String username, String phoneValue) {
        String normalizedUsername = username.trim();
        String normalizedPhone = normalizeNullable(phoneValue);
        userRepository.findByFactoryIdAndUsername(factoryId, normalizedUsername)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new ApiException(HttpStatus.CONFLICT, "该账号已存在");
                });
        if (StringUtils.hasText(normalizedPhone)) {
            userRepository.findByFactoryIdAndPhone(factoryId, normalizedPhone)
                    .filter(existing -> !existing.getId().equals(userId))
                    .ifPresent(existing -> {
                        throw new ApiException(HttpStatus.CONFLICT, "该手机号已存在");
                    });
        }
    }

    private void ensureEditableRole(Long operatorUserId, Long userId, Role role) {
        if (operatorUserId.equals(userId) && role != Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不能将当前登录账号改为生产人员");
        }
    }

    private String normalizeNullable(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    @Transactional(readOnly = true)
    public String exportUsersCsv(Long factoryId) {
        List<FactoryUser> users = userRepository.findByFactoryIdOrderByCreatedAtAsc(factoryId);
        StringBuilder sb = new StringBuilder();
        sb.append("账号,姓名,角色,手机号,部门,密码哈希,虚拟用户,状态\n");
        for (FactoryUser u : users) {
            sb.append(csvEscape(u.getUsername())).append(',');
            sb.append(csvEscape(u.getName())).append(',');
            sb.append(u.getRole().name()).append(',');
            sb.append(csvEscape(u.getPhone())).append(',');
            sb.append(csvEscape(u.getDepartment() != null ? u.getDepartment().getName() : "")).append(',');
            sb.append(csvEscape(u.getPasswordHash())).append(',');
            sb.append(u.isVirtualUser() ? "是" : "否").append(',');
            sb.append(u.isEnabled() ? "启用" : "停用").append('\n');
        }
        return sb.toString();
    }

    @Transactional
    public ImportResult importUsersCsv(Long factoryId, String csvContent) {
        Factory factory = factoryRepository.findById(factoryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "工厂不存在"));

        String[] lines = csvContent.split("\\r?\\n");
        if (lines.length < 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CSV文件格式不正确，至少需要表头和一行数据");
        }

        int successCount = 0;
        int failCount = 0;
        List<String> errors = new java.util.ArrayList<>();

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] cols = parseCsvLine(line);
            if (cols.length < 6) {
                failCount++;
                errors.add("第 " + (i + 1) + " 行：列数不足，已跳过");
                continue;
            }

            String username = cols[0].trim();
            String name = cols[1].trim();
            String roleStr = cols[2].trim();
            String phone = cols.length > 3 ? cols[3].trim() : "";
            String department = cols.length > 4 ? cols[4].trim() : "";
            String passwordOrHash = cols.length > 5 ? cols[5].trim() : "123456";
            boolean isVirtual = cols.length > 6 && "是".equals(cols[6].trim());
            boolean isEnabled = cols.length <= 7 || !"停用".equals(cols[7].trim());

            if (username.isEmpty() || name.isEmpty()) {
                failCount++;
                errors.add("第 " + (i + 1) + " 行：账号或姓名为空，已跳过");
                continue;
            }

            if (userRepository.findByFactoryIdAndUsername(factoryId, username).isPresent()) {
                failCount++;
                errors.add("第 " + (i + 1) + " 行「" + username + "」：该账号已存在，已跳过");
                continue;
            }

            String normalizedPhone = normalizeNullable(phone);
            if (StringUtils.hasText(normalizedPhone) && userRepository.findByFactoryIdAndPhone(factoryId, normalizedPhone).isPresent()) {
                failCount++;
                errors.add("第 " + (i + 1) + " 行「" + username + "」：该手机号已存在，已跳过");
                continue;
            }

            try {
                Role role;
                try {
                    role = Role.valueOf(roleStr);
                } catch (IllegalArgumentException e) {
                    role = roleStr.contains("生产") ? Role.PRODUCTION : Role.ADMIN;
                }

                LocalDateTime now = LocalDateTime.now();
                FactoryUser user = new FactoryUser();
                user.setFactory(factory);
                user.setUsername(username);
                user.setPhone(normalizedPhone);
                user.setName(name);
                if (passwordOrHash.startsWith("$2")) {
                    user.setPasswordHash(passwordOrHash);
                } else {
                    user.setPasswordHash(passwordEncoder.encode(passwordOrHash));
                }
                user.setRole(role);
                String normalizedDeptName = normalizeNullable(department);
                if (normalizedDeptName != null) {
                    user.setDepartment(departmentRepository.findByFactoryIdAndName(factoryId, normalizedDeptName).orElse(null));
                }
                user.setVirtualUser(isVirtual);
                user.setEnabled(isEnabled);
                user.setCreatedAt(now);
                user.setUpdatedAt(now);
                userRepository.save(user);
                successCount++;
            } catch (Exception e) {
                failCount++;
                errors.add("第 " + (i + 1) + " 行「" + username + "」：" + e.getMessage());
            }
        }

        return new ImportResult(lines.length - 1, successCount, failCount, errors);
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String[] parseCsvLine(String line) {
        List<String> result = new java.util.ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        result.add(sb.toString());
        return result.toArray(new String[0]);
    }

    public static class ImportResult {
        private final int total;
        private final int success;
        private final int fail;
        private final List<String> errors;

        public ImportResult(int total, int success, int fail, List<String> errors) {
            this.total = total;
            this.success = success;
            this.fail = fail;
            this.errors = errors;
        }

        public int getTotal() { return total; }
        public int getSuccess() { return success; }
        public int getFail() { return fail; }
        public List<String> getErrors() { return errors; }
    }
}

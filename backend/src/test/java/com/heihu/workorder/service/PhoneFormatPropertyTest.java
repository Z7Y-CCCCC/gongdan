package com.heihu.workorder.service;

import com.heihu.workorder.dto.PhonePasswordLoginRequest;
import net.jqwik.api.*;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * 手机号格式校验属性测试（jqwik）
 *
 * Feature: phone-login, Property 8: 手机号格式校验
 * **Validates: Requirements 4.1, 4.3**
 */
class PhoneFormatPropertyTest {

    private static final Validator validator;

    static {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private static final String PHONE_REGEX = "^1\\d{10}$";

    // Feature: phone-login, Property 8: 手机号格式校验
    // **Validates: Requirements 4.1, 4.3**
    @Property(tries = 200)
    void phoneFormatValidation_shouldMatchRegexExactly(@ForAll("anyStrings") String input) {
        boolean matchesRegex = input.matches(PHONE_REGEX);

        // Create a PhonePasswordLoginRequest with the input as phone and a valid password
        PhonePasswordLoginRequest request = new PhonePasswordLoginRequest();
        request.setPhone(input);
        request.setPassword("validPassword123");

        Set<ConstraintViolation<PhonePasswordLoginRequest>> violations = validator.validate(request);

        // Filter violations to only those on the "phone" field related to Pattern
        Set<ConstraintViolation<PhonePasswordLoginRequest>> phonePatternViolations = violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals("phone"))
                .filter(v -> v.getMessage().contains("手机号格式不正确"))
                .collect(Collectors.toSet());

        if (matchesRegex) {
            // If the input matches the regex, there should be no pattern violation on phone
            assertThat(phonePatternViolations)
                    .as("Valid phone '%s' should have no pattern violations", input)
                    .isEmpty();
        } else {
            // If the input does NOT match the regex, there should be a pattern violation on phone
            // Note: if input is blank, @NotBlank will also fire, but we specifically check for pattern violation
            // For blank/empty strings, @NotBlank fires but @Pattern may not fire (depends on implementation)
            // javax.validation: @Pattern does NOT validate null/empty by default, so we only check non-blank inputs
            if (input != null && !input.trim().isEmpty()) {
                assertThat(phonePatternViolations)
                        .as("Invalid phone '%s' should have pattern violation", input)
                        .isNotEmpty();
            }
        }
    }

    @Provide
    Arbitrary<String> anyStrings() {
        // Mix of valid and invalid phone strings
        return Arbitraries.oneOf(
                // Valid phone numbers: start with 1, followed by 10 digits
                validPhoneNumbers(),
                // Too short (less than 11 chars, digits starting with 1)
                Arbitraries.strings().withCharRange('0', '9').ofMinLength(1).ofMaxLength(10)
                        .map(s -> "1" + s)
                        .filter(s -> s.length() < 11),
                // Too long (more than 11 chars, digits starting with 1)
                Arbitraries.strings().withCharRange('0', '9').ofMinLength(11).ofMaxLength(20)
                        .map(s -> "1" + s),
                // Doesn't start with 1 (starts with 0 or 2-9)
                Arbitraries.strings().withCharRange('0', '9').ofLength(10)
                        .flatMap(suffix -> Arbitraries.of("0", "2", "3", "4", "5", "6", "7", "8", "9")
                                .map(prefix -> prefix + suffix)),
                // Contains non-digit characters
                Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(15),
                // Mixed digits and letters
                Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(11)
                        .map(s -> "1" + s)
                        .filter(s -> !s.matches(PHONE_REGEX)),
                // Empty and whitespace strings
                Arbitraries.of("", " ", "  ", "\t", "\n")
        );
    }

    private Arbitrary<String> validPhoneNumbers() {
        // Generate valid phone numbers: "1" + 10 random digits
        return Arbitraries.strings()
                .withCharRange('0', '9')
                .ofLength(10)
                .map(suffix -> "1" + suffix);
    }
}

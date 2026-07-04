package com.heihu.workorder.exception;

import com.heihu.workorder.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void dataIntegrityViolationReturnsConflictWithoutDatabaseDetails() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("foreign key constraint fails: products")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("数据已被其他业务引用，不能执行该操作");
    }

    @Test
    void unknownExceptionReturnsGenericMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleUnknown(
                new RuntimeException("jdbc:mysql://prod-db/internal_table")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("服务器内部错误，请稍后再试");
        assertThat(response.getBody().getMessage()).doesNotContain("prod-db");
    }
}

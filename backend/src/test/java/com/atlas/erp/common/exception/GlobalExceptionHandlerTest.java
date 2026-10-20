package com.atlas.erp.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    @Test
    void integrityFailureLogsOnlySafeMetadataAndKeepsGenericResponse(CapturedOutput output) {
        String sensitiveMarker = "sensitive-database-value-must-not-be-logged";
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "integrity violation",
                new SQLException("duplicate key contains " + sensitiveMarker, "23505"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/users");

        var response = new GlobalExceptionHandler().handleIntegrity(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().message())
                .isEqualTo("A operação conflita com dados existentes (valor duplicado ou registro em uso).");
        assertThat(output.getOut()).contains("sqlState=23505");
        assertThat(output.getOut()).doesNotContain(sensitiveMarker);
    }

    @Test
    void unexpectedFailureDoesNotLogExceptionMessageOrStackTrace(CapturedOutput output) {
        String sensitiveMarker = "sensitive-unexpected-database-value";
        HttpServletRequest request = new MockHttpServletRequest("POST", "/users");

        new GlobalExceptionHandler().handleUnexpected(new IllegalStateException(sensitiveMarker), request);

        assertThat(output.getOut()).contains("exceptionType=IllegalStateException");
        assertThat(output.getOut()).doesNotContain(sensitiveMarker);
    }
}

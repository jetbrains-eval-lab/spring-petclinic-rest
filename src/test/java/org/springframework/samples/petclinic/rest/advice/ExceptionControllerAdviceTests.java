package org.springframework.samples.petclinic.rest.advice;

/**
 * Tests for the ExceptionControllerAdvice class.
 *
 * This test class verifies that the global exception handling mechanism correctly handles
 * various types of exceptions that can occur in the application and returns appropriate
 * HTTP responses with problem details.
 *
 * Exception types tested:
 * - General exceptions (RuntimeException) - 500 Internal Server Error
 * - Data integrity violations (DataIntegrityViolationException) - 404 Not Found
 * - Validation errors (MethodArgumentNotValidException) - 400 Bad Request
 * - Malformed request bodies (HttpMessageNotReadableException) - 400 Bad Request
 * - Type mismatches (MethodArgumentTypeMismatchException) - 400 Bad Request
 * - Resource not found (NoResourceFoundException) - 404 Not Found
 * - Access denied (AccessDeniedException) - 403 Forbidden
 * - Unsupported HTTP methods (HttpRequestMethodNotSupportedException) - 405 Method Not Allowed
 *
 * Each test verifies:
 * 1. The correct HTTP status code is returned
 * 2. The response contains appropriate error details in the ProblemDetail format
 * 3. The exception type is correctly identified in the response
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.service.clinicService.ApplicationTestConfig;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ContextConfiguration(classes = ApplicationTestConfig.class)
@WebAppConfiguration
class ExceptionControllerAdviceTests {

    private MockMvc mockMvc;

    // Test controller that will throw various exceptions
    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/general-exception")
        public ResponseEntity<String> throwGeneralException() {
            throw new RuntimeException("General error");
        }

        @GetMapping("/data-integrity")
        public ResponseEntity<String> throwDataIntegrityViolationException() {
            throw new DataIntegrityViolationException("Data integrity violation");
        }

        @PostMapping("/validation")
        public ResponseEntity<String> validateRequest(@Valid @RequestBody TestDto dto) {
            return ResponseEntity.ok("Valid");
        }

        @GetMapping("/type-mismatch/{id}")
        public ResponseEntity<String> getWithTypeParameter(@PathVariable("id") Integer id) {
            return ResponseEntity.ok("ID: " + id);
        }

        @GetMapping("/access-denied")
        public ResponseEntity<String> throwAccessDeniedException() {
            throw new AccessDeniedException("Access denied");
        }

        // This endpoint only supports GET
        @GetMapping("/method-not-allowed")
        public ResponseEntity<String> methodNotAllowed() {
            return ResponseEntity.ok("GET supported");
        }
    }

    // Simple DTO for validation tests
    static class TestDto {
        @NotBlank
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new ExceptionControllerAdvice())
            .build();
    }

    @Test
    void testHandleGeneralException() throws Exception {
        mockMvc.perform(get("/test/general-exception")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.title").value("RuntimeException"))
            .andExpect(jsonPath("$.detail").value("General error"));
    }

    @Test
    void testHandleDataIntegrityViolationException() throws Exception {
        mockMvc.perform(get("/test/data-integrity")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("DataIntegrityViolationException"))
            .andExpect(jsonPath("$.detail").value("Data integrity violation"));
    }

    @Test
    void testHandleMethodArgumentNotValidException() throws Exception {
        // This exception is typically thrown during validation of request bodies
        // We'll test it by sending an invalid request body to a controller endpoint
        String invalidJson = "{\"name\":\"\"}"; // Empty name which should fail validation

        mockMvc.perform(post("/test/validation")
                .content(invalidJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("MethodArgumentNotValidException"));
    }

    @Test
    void testHandleHttpMessageNotReadableException() throws Exception {
        // Send malformed JSON to trigger HttpMessageNotReadableException
        String malformedJson = "{\"name\":}"; // Missing value after colon

        mockMvc.perform(post("/test/validation")
                .content(malformedJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("HttpMessageNotReadableException"))
            .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void testHandleMethodArgumentTypeMismatchException() throws Exception {
        // Using a string where an integer is expected should trigger MethodArgumentTypeMismatchException
        mockMvc.perform(get("/test/type-mismatch/abc") // 'abc' is not a valid integer ID
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("MethodArgumentTypeMismatchException"))
            .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void testHandleAccessDeniedException() throws Exception {
        mockMvc.perform(get("/test/access-denied")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title").value("AccessDeniedException"))
            .andExpect(jsonPath("$.detail").value("Access denied: Access denied"));
    }

    @Test
    void testHandleHttpRequestMethodNotSupportedException() throws Exception {
        // Try to DELETE a resource that only supports GET
        mockMvc.perform(delete("/test/method-not-allowed")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.title").value("HttpRequestMethodNotSupportedException"))
            .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void testHandleNoResourceFoundException() throws Exception {
        // Request a non-existent resource
        mockMvc.perform(get("/test/non-existent-path")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }
}

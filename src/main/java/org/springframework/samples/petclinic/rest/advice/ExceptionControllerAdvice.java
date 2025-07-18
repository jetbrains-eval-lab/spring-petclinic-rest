/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.rest.advice;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.samples.petclinic.rest.controller.BindingErrorsResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Global Exception handler for REST controllers.
 * <p>
 * This class handles exceptions thrown by REST controllers and returns appropriate HTTP responses to the client.
 *
 * @author Vitaliy Fedoriv
 * @author Alexander Dudkin
 */
@ControllerAdvice
public class ExceptionControllerAdvice {

    /**
     * Private method for constructing the {@link ProblemDetail} object passing the name and details of the exception
     * class.
     *
     * @param ex     Object referring to the thrown exception.
     * @param status HTTP response status.
     * @param url URL request.
     */
    private ProblemDetail detailBuild(Exception ex, HttpStatus status, StringBuffer url) {
        ProblemDetail detail = ProblemDetail.forStatus(status);
        detail.setType(URI.create(url.toString()));
        detail.setTitle(ex.getClass().getSimpleName());
        detail.setDetail(ex.getLocalizedMessage());
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }

    /**
     * Handles all general exceptions by returning a 500 Internal Server Error status with error details.
     *
     * @param e The {@link Exception} to be handled
     * @param request {@link HttpServletRequest} object referring to the current request.
     * @return A {@link ResponseEntity} containing the error information and a 500 Internal Server Error status
     */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<ProblemDetail> handleGeneralException(Exception e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ProblemDetail detail = this.detailBuild(e, status, request.getRequestURL());
        return ResponseEntity.status(status).body(detail);
    }

    /**
     * Handles {@link DataIntegrityViolationException} which typically indicates database constraint violations. This
     * method returns a 404 Not Found status if an entity does not exist.
     *
     * @param ex The {@link DataIntegrityViolationException} to be handled
     * @param request {@link HttpServletRequest} object referring to the current request.
     * @return A {@link ResponseEntity} containing the error information and a 404 Not Found status
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseBody
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolationException(DataIntegrityViolationException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        ProblemDetail detail = this.detailBuild(ex, status, request.getRequestURL());
        return ResponseEntity.status(status).body(detail);
    }

    /**
     * Handles exception thrown by Bean Validation on controller methods parameters
     *
     * @param ex The {@link MethodArgumentNotValidException} to be handled
     * @param request {@link HttpServletRequest} object referring to the current request.
     * @return A {@link ResponseEntity} containing the error information and a 400 Bad Request status.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        BindingErrorsResponse errors = new BindingErrorsResponse();
        BindingResult bindingResult = ex.getBindingResult();
        if (bindingResult.hasErrors()) {
            errors.addAllErrors(bindingResult);
            ProblemDetail detail = this.detailBuild(ex, status, request.getRequestURL());
            return ResponseEntity.status(status).body(detail);
        }
        return ResponseEntity.status(status).build();
    }

    /**
     * Handles {@link HttpMessageNotReadableException} which occurs when the request body is malformed or
     * cannot be deserialized into the required object.
     *
     * @param ex The {@link HttpMessageNotReadableException} to be handled
     * @param request {@link HttpServletRequest} object referring to the current request.
     * @return A {@link ResponseEntity} containing the error information and a 400 Bad Request status
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseBody
    public ResponseEntity<ProblemDetail> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetail detail = this.detailBuild(ex, status, request.getRequestURL());
        detail.setDetail("Malformed JSON request: " + ex.getLocalizedMessage());
        return ResponseEntity.status(status).body(detail);
    }

    /**
     * Handles {@link MethodArgumentTypeMismatchException} which occurs when a method argument is not
     * the expected type.
     *
     * @param ex The {@link MethodArgumentTypeMismatchException} to be handled
     * @param request {@link HttpServletRequest} object referring to the current request.
     * @return A {@link ResponseEntity} containing the error information and a 400 Bad Request status
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseBody
    public ResponseEntity<ProblemDetail> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetail detail = this.detailBuild(ex, status, request.getRequestURL());
        detail.setDetail("Type mismatch for parameter '" + ex.getName() + "': " + ex.getLocalizedMessage());
        return ResponseEntity.status(status).body(detail);
    }

    /**
     * Handles {@link NoHandlerFoundException} and {@link NoResourceFoundException} which occur when
     * a requested resource is not found.
     *
     * @param ex The exception to be handled
     * @param request {@link HttpServletRequest} object referring to the current request.
     * @return A {@link ResponseEntity} containing the error information and a 404 Not Found status
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    @ResponseBody
    public ResponseEntity<ProblemDetail> handleResourceNotFoundException(Exception ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        ProblemDetail detail = this.detailBuild(ex, status, request.getRequestURL());
        detail.setDetail("The requested resource was not found: " + request.getRequestURI());
        return ResponseEntity.status(status).body(detail);
    }

    /**
     * Handles {@link AccessDeniedException} which occurs when a user does not have permission to
     * access a resource.
     *
     * @param ex The {@link AccessDeniedException} to be handled
     * @param request {@link HttpServletRequest} object referring to the current request.
     * @return A {@link ResponseEntity} containing the error information and a 403 Forbidden status
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseBody
    public ResponseEntity<ProblemDetail> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        ProblemDetail detail = this.detailBuild(ex, status, request.getRequestURL());
        detail.setDetail("Access denied: " + ex.getLocalizedMessage());
        return ResponseEntity.status(status).body(detail);
    }

    /**
     * Handles {@link HttpRequestMethodNotSupportedException} which occurs when a request is made with
     * an unsupported HTTP method.
     *
     * @param ex The {@link HttpRequestMethodNotSupportedException} to be handled
     * @param request {@link HttpServletRequest} object referring to the current request.
     * @return A {@link ResponseEntity} containing the error information and a 405 Method Not Allowed status
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseBody
    public ResponseEntity<ProblemDetail> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.METHOD_NOT_ALLOWED;
        ProblemDetail detail = this.detailBuild(ex, status, request.getRequestURL());
        detail.setDetail("Method not allowed: " + ex.getMethod() + ". Supported methods are: " + String.join(", ", ex.getSupportedMethods()));
        return ResponseEntity.status(status).body(detail);
    }
}

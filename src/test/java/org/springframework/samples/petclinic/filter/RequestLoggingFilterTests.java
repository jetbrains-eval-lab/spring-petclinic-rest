package org.springframework.samples.petclinic.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link RequestLoggingFilter}
 */
class RequestLoggingFilterTests {

    private RequestLoggingFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        // Set up the filter
        filter = new RequestLoggingFilter();

        // Set up the request, response, and filter chain
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();

        // Set up the logger and appender to capture log messages
        logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @Test
    void testDoFilterLogsRequestWithoutQueryString() throws IOException, ServletException {
        // Set up the request
        request.setMethod("GET");
        request.setRequestURI("/api/owners");

        // Execute the filter
        filter.doFilter(request, response, filterChain);

        // Verify the log message
        List<ILoggingEvent> logEvents = listAppender.list;
        assertEquals(1, logEvents.size());

        ILoggingEvent logEvent = logEvents.get(0);
        assertEquals(Level.INFO, logEvent.getLevel());
        assertEquals("Incoming request: GET /api/owners", logEvent.getFormattedMessage());
    }

    @Test
    void testDoFilterLogsRequestWithQueryString() throws IOException, ServletException {
        // Set up the request
        request.setMethod("GET");
        request.setRequestURI("/api/owners");
        request.setQueryString("lastName=Smith");

        // Execute the filter
        filter.doFilter(request, response, filterChain);

        // Verify the log message
        List<ILoggingEvent> logEvents = listAppender.list;
        assertEquals(1, logEvents.size());

        ILoggingEvent logEvent = logEvents.get(0);
        assertEquals(Level.INFO, logEvent.getLevel());
        assertEquals("Incoming request: GET /api/owners?lastName=Smith", logEvent.getFormattedMessage());
    }

    @Test
    void testDoFilterLogsDifferentHttpMethods() throws IOException, ServletException {
        // Test POST request
        request.setMethod("POST");
        request.setRequestURI("/api/owners");
        MockFilterChain postFilterChain = new MockFilterChain();

        filter.doFilter(request, response, postFilterChain);

        // Test PUT request
        MockHttpServletRequest putRequest = new MockHttpServletRequest();
        putRequest.setMethod("PUT");
        putRequest.setRequestURI("/api/owners/1");
        MockFilterChain putFilterChain = new MockFilterChain();

        filter.doFilter(putRequest, response, putFilterChain);

        // Test DELETE request
        MockHttpServletRequest deleteRequest = new MockHttpServletRequest();
        deleteRequest.setMethod("DELETE");
        deleteRequest.setRequestURI("/api/owners/1");
        MockFilterChain deleteFilterChain = new MockFilterChain();

        filter.doFilter(deleteRequest, response, deleteFilterChain);

        // Verify the log messages
        List<ILoggingEvent> logEvents = listAppender.list;
        assertEquals(3, logEvents.size());

        assertEquals("Incoming request: POST /api/owners", logEvents.get(0).getFormattedMessage());
        assertEquals("Incoming request: PUT /api/owners/1", logEvents.get(1).getFormattedMessage());
        assertEquals("Incoming request: DELETE /api/owners/1", logEvents.get(2).getFormattedMessage());
    }
}

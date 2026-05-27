/*
 * Copyright 2002-2013 the original author or authors.
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
package org.springframework.samples.petclinic.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service implementation for API key audit logging operations.
 *
 * @author Spring PetClinic Team
 */
@Service
@Profile("spring-data-jpa")
public class ApiKeyAuditServiceImpl implements ApiKeyAuditService {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyAuditServiceImpl.class);

    private final boolean auditEnabled;
    private final int failureThreshold;
    private final Duration timeWindow;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> failureHistory = new ConcurrentHashMap<>();

    public ApiKeyAuditServiceImpl(
            @Value("${petclinic.apikey.audit.enabled:true}") boolean auditEnabled,
            @Value("${petclinic.apikey.suspicious-activity.failure-threshold:5}") int failureThreshold,
            @Value("${petclinic.apikey.suspicious-activity.time-window-minutes:15}") int timeWindowMinutes) {
        this.auditEnabled = auditEnabled;
        this.failureThreshold = failureThreshold;
        this.timeWindow = Duration.ofMinutes(timeWindowMinutes);
        this.clock = Clock.systemUTC();
    }

    @Override
    public void logAuthenticationAttempt(String method, String path, String clientIp, String userAgent,
                                         Integer apiKeyId, String keyPrefix, boolean success,
                                         String failureReason, boolean suspicious) {
        if (!auditEnabled) {
            return;
        }

        String normalizedMethod = method != null ? method : "UNKNOWN";
        String normalizedPath = path != null ? path : "UNKNOWN";
        String normalizedIp = clientIp != null ? clientIp : "UNKNOWN";
        String normalizedUserAgent = userAgent != null ? userAgent : "UNKNOWN";
        String normalizedPrefix = keyPrefix != null ? keyPrefix : "UNKNOWN";
        String normalizedReason = failureReason != null ? failureReason : "NONE";

        Instant now = Instant.now(clock);

        logger.info(
            "event=api_key_auth success={} failure_reason={} key_prefix={} method={} path={} client_ip={} user_agent=\"{}\" timestamp={} suspicious={} api_key_id={}",
            success,
            normalizedReason,
            normalizedPrefix,
            normalizedMethod,
            normalizedPath,
            normalizedIp,
            normalizedUserAgent,
            now.toString(),
            suspicious,
            apiKeyId
        );
    }

    @Override
    public boolean detectSuspiciousActivity(String keyPrefix) {
        if (!auditEnabled || keyPrefix == null || "UNKNOWN".equals(keyPrefix)) {
            return false;
        }

        Deque<Instant> deque = failureHistory.get(keyPrefix);
        if (deque == null) {
            return false;
        }

        Instant now = Instant.now(clock);
        pruneOldFailures(deque, now);
        return deque.size() >= failureThreshold;
    }

    @Override
    public boolean recordFailureAndCheck(String keyPrefix) {
        if (!auditEnabled || keyPrefix == null || "UNKNOWN".equals(keyPrefix)) {
            return false;
        }
        Instant now = Instant.now(clock);
        Deque<Instant> deque = recordFailure(keyPrefix, now);
        synchronized (deque) {
            pruneOldFailures(deque, now);
            return deque.size() >= failureThreshold;
        }
    }

    private Deque<Instant> recordFailure(String keyPrefix, Instant now) {
        Deque<Instant> deque = failureHistory.computeIfAbsent(keyPrefix, k -> new ConcurrentLinkedDeque<>());
        synchronized (deque) {
            deque.addLast(now);
            pruneOldFailures(deque, now);
        }
        return deque;
    }

    private void pruneOldFailures(Deque<Instant> deque, Instant now) {
        Instant cutoff = now.minus(timeWindow);
        while (true) {
            Instant head = deque.peekFirst();
            if (head == null || !head.isBefore(cutoff)) {
                break;
            }
            deque.pollFirst();
        }
    }
}

/*
 * Copyright 2024 the original author or authors.
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
package org.springframework.samples.petclinic.performance;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;

/**
 * Test configuration for N+1 problem testing.
 * Provides SQL query interceptor for counting database queries.
 */
@TestConfiguration
public class N1TestConfiguration {

    @Bean
    public SqlQueryInterceptor sqlQueryInterceptor() {
        return new SqlQueryInterceptor();
    }

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer(SqlQueryInterceptor sqlQueryInterceptor) {
        return hibernateProperties -> {
            hibernateProperties.put("hibernate.session_factory.statement_inspector", sqlQueryInterceptor);
        };
    }
}

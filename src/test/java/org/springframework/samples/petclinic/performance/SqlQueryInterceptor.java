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

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SQL Query Interceptor that captures all executed SQL queries for performance analysis.
 * This component is essential for demonstrating N+1 select problems and measuring optimization effectiveness.
 */
public class SqlQueryInterceptor implements StatementInspector {

    private final List<String> executedQueries = new CopyOnWriteArrayList<>();
    private long startTime;
    private boolean enabled = false;

    /**
     * Start capturing SQL queries and reset metrics.
     */
    public void startCapturing() {
        this.enabled = true;
        this.executedQueries.clear();
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Stop capturing SQL queries.
     */
    public void stopCapturing() {
        this.enabled = false;
    }

    /**
     * Reset all captured data.
     */
    public void reset() {
        this.executedQueries.clear();
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Get the number of queries executed since capturing started.
     *
     * @return query count
     */
    public int getQueryCount() {
        return executedQueries.size();
    }

    /**
     * Get all executed queries since capturing started.
     *
     * @return list of SQL queries
     */
    public List<String> getExecutedQueries() {
        return new ArrayList<>(executedQueries);
    }

    /**
     * Get the total execution time since capturing started.
     *
     * @return execution time in milliseconds
     */
    public long getExecutionTime() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Get performance metrics summary.
     *
     * @return QueryMetrics object containing all performance data
     */
    public QueryMetrics getMetrics() {
        return new QueryMetrics(
            getQueryCount(),
            getExecutionTime(),
            getExecutedQueries(),
            analyzeQueryTypes()
        );
    }

    /**
     * Check if the interceptor is currently capturing queries.
     *
     * @return true if capturing is enabled
     */
    public boolean isCapturing() {
        return enabled;
    }

    @Override
    public String inspect(String sql) {
        if (enabled && sql != null) {
            // Clean up the SQL for better readability
            String cleanSql = sql.trim().replaceAll("\\s+", " ");
            executedQueries.add(cleanSql);
        }
        return sql;
    }

    /**
     * Analyze the types of queries executed to identify patterns.
     *
     * @return QueryTypeAnalysis containing breakdown of query types
     */
    private QueryTypeAnalysis analyzeQueryTypes() {
        int selectCount = 0;
        int insertCount = 0;
        int updateCount = 0;
        int deleteCount = 0;
        int joinCount = 0;

        for (String query : executedQueries) {
            String upperQuery = query.toUpperCase();

            if (upperQuery.startsWith("SELECT")) {
                selectCount++;
                if (upperQuery.contains("JOIN")) {
                    joinCount++;
                }
            } else if (upperQuery.startsWith("INSERT")) {
                insertCount++;
            } else if (upperQuery.startsWith("UPDATE")) {
                updateCount++;
            } else if (upperQuery.startsWith("DELETE")) {
                deleteCount++;
            }
        }

        return new QueryTypeAnalysis(selectCount, insertCount, updateCount, deleteCount, joinCount);
    }

    /**
     * Data class to hold query performance metrics.
     */
    public static class QueryMetrics {
        private final int queryCount;
        private final long executionTimeMs;
        private final List<String> queries;
        private final QueryTypeAnalysis queryTypeAnalysis;

        public QueryMetrics(int queryCount, long executionTimeMs, List<String> queries, QueryTypeAnalysis queryTypeAnalysis) {
            this.queryCount = queryCount;
            this.executionTimeMs = executionTimeMs;
            this.queries = new ArrayList<>(queries);
            this.queryTypeAnalysis = queryTypeAnalysis;
        }

        public int getQueryCount() { return queryCount; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public List<String> getQueries() { return new ArrayList<>(queries); }
        public QueryTypeAnalysis getQueryTypeAnalysis() { return queryTypeAnalysis; }

        @Override
        public String toString() {
            return String.format("QueryMetrics{queryCount=%d, executionTimeMs=%d, selectQueries=%d, joinQueries=%d}",
                queryCount, executionTimeMs, queryTypeAnalysis.getSelectCount(), queryTypeAnalysis.getJoinCount());
        }
    }

    /**
     * Data class to hold query type analysis.
     */
    public static class QueryTypeAnalysis {
        private final int selectCount;
        private final int insertCount;
        private final int updateCount;
        private final int deleteCount;
        private final int joinCount;

        public QueryTypeAnalysis(int selectCount, int insertCount, int updateCount, int deleteCount, int joinCount) {
            this.selectCount = selectCount;
            this.insertCount = insertCount;
            this.updateCount = updateCount;
            this.deleteCount = deleteCount;
            this.joinCount = joinCount;
        }

        public int getSelectCount() { return selectCount; }
        public int getInsertCount() { return insertCount; }
        public int getUpdateCount() { return updateCount; }
        public int getDeleteCount() { return deleteCount; }
        public int getJoinCount() { return joinCount; }

        /**
         * Indicates if this looks like an N+1 problem pattern.
         * N+1 is characterized by many SELECT queries with few or no JOINs.
         *
         * @return true if pattern suggests N+1 problem
         */
        public boolean isLikelyN1Problem() {
            return selectCount > 5 && joinCount < (selectCount / 3);
        }

        @Override
        public String toString() {
            return String.format("QueryTypeAnalysis{select=%d, insert=%d, update=%d, delete=%d, join=%d, likelyN1=%s}",
                selectCount, insertCount, updateCount, deleteCount, joinCount, isLikelyN1Problem());
        }
    }
}

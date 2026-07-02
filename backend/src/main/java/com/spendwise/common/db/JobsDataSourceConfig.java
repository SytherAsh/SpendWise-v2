package com.spendwise.common.db;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Two connection pools against the same database, two different Postgres roles.
 *
 * <p>Both beans are built from the same {@link JdbcConnectionDetails} bean (Boot 3.1+'s
 * connection-abstraction — NOT raw {@code spring.datasource.*} properties directly) so this stays
 * correct under Testcontainers' {@code @ServiceConnection}, which overrides {@link
 * JdbcConnectionDetails}, not the properties themselves: reading the properties directly here
 * would silently reconnect to the wrong (non-ephemeral) database in every integration test.
 * {@code JdbcConnectionDetailsConfiguration}'s own auto-configured fallback bean (derived from
 * {@code spring.datasource.*} when no {@code @ServiceConnection} is present) is conditional on
 * {@code @ConditionalOnMissingBean(JdbcConnectionDetails.class)}, not on {@code DataSource.class},
 * so it stays active regardless of the {@link DataSource} beans defined below.
 *
 * <p>Defining an explicit {@code @Primary} {@code dataSource} bean here makes {@code
 * DataSourceAutoConfiguration}'s own bean back off ({@code
 * @ConditionalOnMissingBean(DataSource.class)}), which is required once a second {@link
 * DataSource} bean exists — otherwise every existing unqualified {@link JdbcTemplate} injection
 * (every repository in the app) would become ambiguous.
 *
 * <p>{@code jobsDataSource}/{@code jobsJdbcTemplate} connect as {@code spendwise_jobs}
 * (db-init/02-jobs-role.sql — {@code BYPASSRLS}, same host/port/database as the primary
 * connection, different credentials) and must be injected with {@code
 * @Qualifier("jobsJdbcTemplate")} by name explicitly; only {@code
 * com.spendwise.categorization}'s scheduled-job read paths (E4-S3-T3/T4) may do so — see
 * implementation/tracking/STATUS.md's Epic 4 close-out for why this exists at all.
 */
@Configuration
public class JobsDataSourceConfig {

    @Primary
    @Bean
    public DataSource dataSource(JdbcConnectionDetails connectionDetails) {
        return DataSourceBuilder.create()
                .url(connectionDetails.getJdbcUrl())
                .username(connectionDetails.getUsername())
                .password(connectionDetails.getPassword())
                .build();
    }

    @Bean
    public DataSource jobsDataSource(
            JdbcConnectionDetails connectionDetails,
            @Value("${app.datasource.jobs.username}") String jobsUsername,
            @Value("${app.datasource.jobs.password}") String jobsPassword) {
        // Same host/port/database as the primary connection (including the ephemeral
        // Testcontainers one in integration tests) — only the role differs.
        HikariDataSource dataSource = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(connectionDetails.getJdbcUrl())
                .username(jobsUsername)
                .password(jobsPassword)
                .build();
        // spendwise_jobs (db-init/02-jobs-role.sql) doesn't exist in every environment that
        // loads this Spring context — e.g. a bare Testcontainers Postgres image only has it if a
        // specific test bootstraps it (see CategorizationJobsIntegrationTest). Hikari's default
        // initializationFailTimeout (1ms) would otherwise fail context startup for every OTHER
        // integration test the moment this bean is created, even ones that never touch a
        // scheduled job. -1 disables that eager check; a real connection is only attempted (and
        // can only fail) when something actually queries via jobsJdbcTemplate.
        dataSource.setInitializationFailTimeout(-1);
        // minimumIdle otherwise defaults to maximumPoolSize (10): Hikari's background
        // connection-adder would then continuously retry, forever, trying to keep 10 idle
        // connections warm against a role that may not exist in this environment — a real
        // incident found in CI (E4-S3-T3/T4 close-out): every OTHER integration test's Spring
        // context degraded to ~30s per request (HikariCP's default connectionTimeout) once
        // CategorizationRetryJob's immediate first firing triggered that retry storm, starving
        // the whole JVM of CPU/threads on the runner. 0 means Hikari only ever opens a
        // connection on actual demand, never speculatively. A small maximumPoolSize is enough —
        // this pool serves two low-concurrency scheduled jobs, not request traffic.
        dataSource.setMinimumIdle(0);
        dataSource.setMaximumPoolSize(2);
        return dataSource;
    }

    @Bean
    public JdbcTemplate jobsJdbcTemplate(@Qualifier("jobsDataSource") DataSource jobsDataSource) {
        return new JdbcTemplate(jobsDataSource);
    }
}

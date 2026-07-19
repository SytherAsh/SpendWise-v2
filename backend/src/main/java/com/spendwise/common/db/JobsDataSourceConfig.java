package com.spendwise.common.db;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.ObjectProvider;
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
 * <p>Both {@link DataSource} beans are built from the same {@link JdbcConnectionDetails} bean
 * (Boot 3.1+'s connection-abstraction — NOT raw {@code spring.datasource.*} properties directly)
 * so this stays correct under Testcontainers' {@code @ServiceConnection}, which overrides {@link
 * JdbcConnectionDetails}, not the properties themselves: reading the properties directly here
 * would silently reconnect to the wrong (non-ephemeral) database in every integration test.
 *
 * <p>When {@code @ServiceConnection} is absent (a real {@code bootRun}/prod boot), Spring Boot 3.5
 * does <b>not</b> register a {@link JdbcConnectionDetails} fallback bean here (an earlier version of
 * this class assumed it did — the assumption held in integration tests, where {@code
 * @ServiceConnection} supplies the bean, but failed context startup on the first real {@code
 * bootRun}, 2026-07-05). Both bean methods below therefore inject an {@code
 * ObjectProvider<JdbcConnectionDetails>} and fall back to the {@code spring.datasource.*} properties
 * directly when no such bean exists — while still preferring {@link JdbcConnectionDetails} whenever
 * it IS present, so the ephemeral Testcontainers connection still wins in integration tests.
 *
 * <p>Defining an explicit {@code @Primary} {@code dataSource} bean here makes {@code
 * DataSourceAutoConfiguration}'s own bean back off ({@code
 * @ConditionalOnMissingBean(DataSource.class)}), which is required once a second {@link
 * DataSource} bean exists — otherwise every existing unqualified {@link JdbcTemplate} injection
 * (every repository in the app) would become ambiguous.
 *
 * <p><b>The {@code jdbcTemplate} bean below is not optional decoration — omitting it is a real
 * incident that shipped once (Epic 4 close-out, 2026-07-02).</b> Spring Boot's own auto-configured
 * {@code JdbcTemplate} is conditional on {@code @ConditionalOnMissingBean(JdbcOperations.class)}
 * — since {@code jobsJdbcTemplate} below is itself a {@code JdbcTemplate} (implements {@code
 * JdbcOperations}), its mere presence in the context silently disables that auto-configuration
 * entirely, leaving {@code jobsJdbcTemplate} as the ONLY {@code JdbcTemplate} bean — meaning
 * every unqualified injection across the whole app (every repository that doesn't explicitly ask
 * for {@code @Qualifier("jobsJdbcTemplate")}) would silently receive the {@code
 * BYPASSRLS}-enabled connection instead of the RLS-enforced one. This is exactly what happened in
 * CI: it surfaced loudly there only because {@code spendwise_jobs} didn't exist in the test
 * container, so every request failed outright — in an environment where the role DOES exist (real
 * Supabase, per db-init/02-jobs-role.sql), it would have worked and silently run the entire
 * application through the RLS-bypassing role instead. Defining this bean explicitly makes Boot's
 * auto-configuration back off for the SAME reason the {@code dataSource} bean above does, and
 * pins the unqualified injection target to the correct (RLS-enforced) pool by construction, not
 * by hoping auto-configuration guesses right.
 *
 * <p>{@code jobsDataSource}/{@code jobsJdbcTemplate} connect as {@code spendwise_jobs}
 * (db-init/02-jobs-role.sql — {@code BYPASSRLS}, same host/port/database as the primary
 * connection, different credentials) and must be injected with {@code
 * @Qualifier("jobsJdbcTemplate")} by name explicitly. Four categories of caller are sanctioned:
 * {@code @Scheduled} job classes across several modules (E4-S3-T3/T4, E5-S2-T4, E6-S2-T1,
 * E8-S2-T1) reading across all users for a background pass — as of ADR-018 (2026-07-19) these are
 * no longer {@code @Scheduled}-annotated (see {@code com.spendwise.common.schedule.DynamicJobScheduler})
 * but are the same cross-user jobs the category always meant; {@code
 * com.spendwise.admin.AdminRepository}'s request-scoped reads (added in Epic 11), since Admin has
 * no per-request "current user" to scope RLS to by construction (it must enumerate every user and
 * read the system-wide {@code admin_logs} table); {@code
 * com.spendwise.common.db.AdminEventLog} (ML strategy phase, 2026-07-19), which writes that same
 * system-wide {@code admin_logs} table on behalf of whichever of the first category's jobs calls
 * it — not a new independent caller pattern, just the write side of the table Admin already reads
 * cross-user; and {@code com.spendwise.common.schedule.JobScheduleRepository} (ADR-018), which
 * reads/writes {@code job_schedules} — another system-wide, non-user table, read from a
 * TaskScheduler thread with no RLS session of its own by construction, same reasoning as
 * {@code AdminRepository}. All four are narrow, audited exceptions to "every query bypassing RLS
 * must be a background job," not a blanket bypass — see implementation/tracking/STATUS.md's
 * Epic 4 close-out for why this role exists at all, and its Epic 11 close-out for the
 * request-scoped broadening.
 */
@Configuration
public class JobsDataSourceConfig {

    @Primary
    @Bean
    public DataSource dataSource(
            ObjectProvider<JdbcConnectionDetails> connectionDetails,
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        JdbcConnectionDetails details = connectionDetails.getIfAvailable();
        return DataSourceBuilder.create()
                .url(details != null ? details.getJdbcUrl() : url)
                .username(details != null ? details.getUsername() : username)
                .password(details != null ? details.getPassword() : password)
                .build();
    }

    /** See the class-level javadoc — this is the fix for a real incident, not decoration. */
    @Primary
    @Bean
    public JdbcTemplate jdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public DataSource jobsDataSource(
            ObjectProvider<JdbcConnectionDetails> connectionDetails,
            @Value("${spring.datasource.url}") String url,
            @Value("${app.datasource.jobs.username}") String jobsUsername,
            @Value("${app.datasource.jobs.password}") String jobsPassword) {
        // Same host/port/database as the primary connection (including the ephemeral
        // Testcontainers one in integration tests) — only the role differs.
        JdbcConnectionDetails details = connectionDetails.getIfAvailable();
        HikariDataSource dataSource = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(details != null ? details.getJdbcUrl() : url)
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
        // minimumIdle otherwise defaults to maximumPoolSize (10) — keep it at 0 with a small
        // maximumPoolSize so a missing-role environment fails once on actual demand rather than
        // Hikari's background connection-adder retrying indefinitely in an idle-pool-warming
        // loop. This pool serves two low-concurrency scheduled jobs, not request traffic.
        dataSource.setMinimumIdle(0);
        dataSource.setMaximumPoolSize(2);
        return dataSource;
    }

    @Bean
    public JdbcTemplate jobsJdbcTemplate(@Qualifier("jobsDataSource") DataSource jobsDataSource) {
        return new JdbcTemplate(jobsDataSource);
    }
}

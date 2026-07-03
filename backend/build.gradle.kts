plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.spendwise"
version = "0.0.1-SNAPSHOT"
description = "SpendWise backend — Spring Boot modular monolith REST API"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Separate integrationTest source set/task per docs/testing.md: unit tests
// (`./gradlew test`) mock dependencies and never need Docker; integration
// tests (`./gradlew integrationTest`) run against a real Postgres container
// via Testcontainers. Keeping them apart means `test` stays fast and doesn't
// silently require Docker just because the app now has a DataSource.
val integrationTest: SourceSet by sourceSets.creating

configurations["integrationTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

// Custom source sets don't automatically see main's output/resources (e.g.
// db/migration) the way the built-in `test` source set does — wire it explicitly.
integrationTest.compileClasspath += sourceSets.main.get().output
integrationTest.runtimeClasspath += sourceSets.main.get().output

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Alerts' SMTP dispatch (E5-S3-T2) — a plain JavaMail client library, not a hosted service,
    // so it's free-tier compatible; configured via EMAIL_SMTP_* (docs/deployment.md).
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Own JWT issuance/validation for the user (JWT_SECRET) and admin
    // (ADMIN_JWT_SECRET) sessions — see E1-S1-T7/E1-S2-T1 for why these stay
    // as two independent filters rather than a shared Spring Security
    // resource-server/OAuth2 setup.
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Firebase Admin SDK — server-side verification of phone-OTP/Google ID
    // tokens (E1-S1-T1). Never used to trust a client-asserted identity.
    implementation("com.google.firebase:firebase-admin:9.4.1")

    // Analytics' PDF export (E7-S2-T2) — a plain library, not a hosted service, so it's
    // free-tier compatible. LGPL/MPL-licensed (fork of pre-AGPL iText 4), chosen over iText 7
    // specifically to avoid AGPL's network-use copyleft obligation for a hosted service.
    implementation("com.github.librepdf:openpdf:2.2.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Module-boundary test (E4-S3-T5): asserts only com.spendwise.categorization depends on
    // MlClient, so "FastAPI is called only from the Categorization module" (CLAUDE.md) stays
    // enforced by a test, not just code review.
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")

    "integrationTestImplementation"("org.springframework.boot:spring-boot-testcontainers")
    "integrationTestImplementation"("org.testcontainers:junit-jupiter")
    "integrationTestImplementation"("org.testcontainers:postgresql")
    "integrationTestImplementation"("org.springframework.security:spring-security-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // See bootRun's jvmArgs comment below — same root cause affects test JVMs
    // that touch the datasource (Testcontainers integration tests, Epic 0 S2+).
    jvmArgs("-Duser.timezone=UTC")
}

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests against a real Postgres Testcontainer."
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.output + integrationTest.runtimeClasspath
    shouldRunAfter(tasks.named("test"))
    // Diagnostic-only (Epic 4 CI incident, 2026-07-02): Gradle's default test logging prints
    // only failure stack traces, suppressing the application's own log output (Spring Boot
    // startup, connection pool activity, request timing) during normal operation. A ~17x
    // suite-wide slowdown appeared after E4-S3-T3/T4 landed and wasn't resolved by the first
    // fix attempt (initialDelay + minimumIdle(0) on the jobs pool) — this surfaces full output
    // so the next CI run's log actually shows what's happening during the slow window, instead
    // of guessing further. Candidate for reverting/toning down once root-caused.
    testLogging {
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        events(
            org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT,
            org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR)
    }
}

tasks.named("check") {
    dependsOn(tasks.named("integrationTest"))
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    // pgjdbc sends the JVM's default zone ID as the Postgres "TimeZone"
    // startup parameter. On machines whose JVM reports an old zone alias
    // (e.g. "Asia/Calcutta", not the current IANA name "Asia/Kolkata"),
    // Postgres rejects the connection outright ("invalid value for
    // parameter TimeZone") regardless of what the JDBC URL says. Forcing
    // the JVM's own default to UTC fixes it at the source.
    jvmArgs("-Duser.timezone=UTC")
}

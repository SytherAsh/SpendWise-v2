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

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

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

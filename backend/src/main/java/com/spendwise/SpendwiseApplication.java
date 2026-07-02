package com.spendwise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling activates the app's @Scheduled background jobs (first landed in E4-S3-T3/T4
// — the categorization retry job and the ML retraining job; docs/architecture.md Background Jobs
// table). All run inside this same Spring Boot process (CLAUDE.md — no external job runner).
@SpringBootApplication
@EnableScheduling
public class SpendwiseApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpendwiseApplication.class, args);
    }
}

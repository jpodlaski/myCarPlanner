package com.sanproject.service_planner.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables @Async outbox dispatch and the @Scheduled poller that retries PENDING email_outbox rows. */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}

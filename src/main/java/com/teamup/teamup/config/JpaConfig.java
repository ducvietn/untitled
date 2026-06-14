package com.teamup.teamup.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables JPA Auditing so that @CreatedDate and @LastModifiedDate
 * are automatically populated by Hibernate.
 *
 * Also enables Spring's @Async annotation for background notification
 * dispatch from the cron scheduler.
 */
@Configuration
@EnableJpaAuditing
@EnableAsync
public class JpaConfig {
    // JpaAuditingHandler is automatically registered by @EnableJpaAuditing
}

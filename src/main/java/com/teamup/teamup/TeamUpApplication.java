package com.teamup.teamup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TeamUp — Group Project Management Platform.
 *
 * Spring Boot entry point.
 * {@code @EnableScheduling} activates @Scheduled cron methods (the "Referee").
 */
@SpringBootApplication
@EnableScheduling
public class TeamUpApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeamUpApplication.class, args);
    }
}

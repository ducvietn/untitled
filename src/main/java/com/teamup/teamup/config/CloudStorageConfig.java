package com.teamup.teamup.config;

import com.cloudinary.Cloudinary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.HashMap;
import java.util.Map;

/**
 * Cloudinary bean configuration.
 *
 * <h3>Profile strategy</h3>
 * <ul>
 *   <li>{@code cloud} profile or when env var {@code CLOUDINARY_CLOUD_NAME} is set →
 *       activates {@link CloudinaryStorageServiceImpl}.</li>
 *   <li>Otherwise → falls back to {@link LocalStorageServiceImpl}
 *       (active in dev without cloud credentials).</li>
 * </ul>
 *
 * Set {@code spring.profiles.active=cloud} or simply ensure
 * {@code CLOUDINARY_CLOUD_NAME}, {@code CLOUDINARY_API_KEY}, and
 * {@code CLOUDINARY_API_SECRET} are present as environment variables
 * (the {@code cloud} profile activates automatically when the env vars are set).
 */
@Configuration
@Slf4j
public class CloudStorageConfig {

    @Value("${cloudinary.cloud-name:#{null}}")
    private String cloudName;

    @Value("${cloudinary.api-key:#{null}}")
    private String apiKey;

    @Value("${cloudinary.api-secret:#{null}}")
    private String apiSecret;

    @Bean
    public Cloudinary cloudinary() {
        if (cloudName == null || cloudName.isBlank()) {
            log.warn("Cloudinary credentials not configured. " +
                "File storage will use the local filesystem. " +
                "Set CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET " +
                "as environment variables to enable Cloudinary.");
            return null;
        }

        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", cloudName);
        config.put("api_key", apiKey);
        config.put("api_secret", apiSecret);
        config.put("secure", "true");

        log.info("Cloudinary configured for cloud: {}", cloudName);
        return new Cloudinary(config);
    }
}

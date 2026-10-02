package com.qr_ordering_system.app.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CORS for a separately-hosted frontend.
 *
 * In development this is inert: the Angular dev server proxies /api and
 * /uploads to the backend, so every request is same-origin and the browser
 * never sends a preflight.
 *
 * The moment the Angular app is served from a different origin than the API
 * (a CDN, a separate subdomain - the normal production setup) the browser
 * sends a preflight for every request that carries an Authorization header,
 * and without these headers every call fails.
 *
 * The allowed origins are configuration rather than "*" on purpose: this API
 * sends Authorization, and a wildcard would let any site issue authenticated
 * cross-origin requests.
 *
 * Note this must be wired into Spring Security via http.cors(...). Security
 * rejects the preflight with 401 during authorization, before the DispatcherServlet
 * (and therefore the MVC CORS handler) ever runs.
 *
 * Set APP_CORS_ALLOWED_ORIGINS to a comma-separated list:
 *   https://app.example.com,https://admin.example.com
 * Left unset, CORS stays disabled - correct for local work and same-origin deploys.
 */
@Configuration
public class CorsConfig {

    /** Any loopback host on any port: the Angular dev server and local tooling. */
    private static final List<String> LOOPBACK_ORIGIN_PATTERNS =
            List.of("http://localhost:[*]", "http://127.0.0.1:[*]");

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    private final List<String> allowedOrigins = new ArrayList<>();

    public CorsConfig(@Value("${app.cors.allowed-origins:}") String origins) {
        if (origins != null && !origins.isBlank()) {
            Arrays.stream(origins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(allowedOrigins::add);
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        if (!allowedOrigins.isEmpty()) {
            config.setAllowedOrigins(allowedOrigins);
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
            config.setExposedHeaders(List.of("Location"));
            // The JWT travels in an Authorization header, not a cookie, so
            // credentials are not needed and wildcard origins stay avoidable.
            config.setAllowCredentials(false);
            log.info("CORS: allowing {} configured origin(s): {}", allowedOrigins.size(), allowedOrigins);
        } else {
            // Unconfigured, so this is a local/development run.
            //
            // Loopback only - NOT a wildcard. Note that "no CORS configuration"
            // does not mean "CORS inactive" once http.cors(...) is enabled:
            // Spring Security then inspects every request carrying an Origin
            // header and rejects anything not on the list, which breaks the
            // Angular dev server (its proxy forwards Origin: http://localhost:4200)
            // with a 403 "Invalid CORS request".
            //
            // Loopback keeps local tooling and the dev server working without
            // opening the API to arbitrary internet origins if this reaches
            // production unconfigured.
            config.setAllowedOriginPatterns(LOOPBACK_ORIGIN_PATTERNS);
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
            config.setExposedHeaders(List.of("Location"));
            config.setAllowCredentials(false);
            log.warn("""
                    CORS: app.cors.allowed-origins is not set, so only loopback origins are allowed. \
                    This is a development default - set APP_CORS_ALLOWED_ORIGINS to your real frontend \
                    origin(s) before deploying.""");
        }

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}

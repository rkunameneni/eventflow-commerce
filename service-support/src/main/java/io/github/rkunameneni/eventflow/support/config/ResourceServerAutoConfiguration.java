package io.github.rkunameneni.eventflow.support.config;

import io.github.rkunameneni.eventflow.support.security.JwtSupport;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless JWT resource-server security. Every service validates tokens itself even behind the
 * gateway (defence in depth: a mis-routed or internal call cannot skip authorization), and uses
 * {@code @PreAuthorize} for role checks on individual endpoints.
 */
@AutoConfiguration(before = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(EventflowProperties.class)
public class ResourceServerAutoConfiguration {

    private static final String[] ALWAYS_PUBLIC = {
        "/actuator/health/**", "/actuator/info", "/actuator/prometheus",
        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error"
    };

    @Bean
    @ConditionalOnMissingBean
    JwtDecoder jwtDecoder(EventflowProperties properties) {
        return JwtSupport.decoder(properties.security().jwtSecret());
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain eventflowSecurityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
            EventflowProperties properties) throws Exception {
        String[] publicGetPaths = properties.security().publicGetPaths().toArray(String[]::new);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(ALWAYS_PUBLIC).permitAll();
                    if (publicGetPaths.length > 0) {
                        auth.requestMatchers(HttpMethod.GET, publicGetPaths).permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(JwtSupport.authenticationConverter())));
        return http.build();
    }
}

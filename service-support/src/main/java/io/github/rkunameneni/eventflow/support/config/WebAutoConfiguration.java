package io.github.rkunameneni.eventflow.support.config;

import io.github.rkunameneni.eventflow.support.web.CorrelationIdFilter;
import io.github.rkunameneni.eventflow.support.web.error.ProblemDetailsAdvice;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * HTTP conventions shared by every servlet service: correlation ids, problem details, OpenAPI.
 * Runs before Spring MVC's auto-configuration so that Boot's default problem-details handler
 * backs off in favour of {@link ProblemDetailsAdvice}.
 */
@AutoConfiguration(before = WebMvcAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebAutoConfiguration {

    private static final String BEARER_SCHEME = "bearer-jwt";

    @Bean
    FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        var registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        // Before Spring Security, so that even rejected requests are logged with an id.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    ProblemDetailsAdvice problemDetailsAdvice() {
        return new ProblemDetailsAdvice();
    }

    @Bean
    @ConditionalOnMissingBean
    OpenAPI eventflowOpenApi(@Value("${spring.application.name}") String applicationName,
            @Value("${eventflow.api.description:EventFlow Commerce service API}") String description) {
        return new OpenAPI()
                .info(new Info()
                        .title(applicationName)
                        .description(description)
                        .version("v1")
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}

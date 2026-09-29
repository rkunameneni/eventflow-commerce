package io.github.rkunameneni.eventflow.support;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Boots a service with a service-specific configuration file ({@code <service>.yml} instead of
 * {@code application.yml}). Distinct names let several services share one classpath, which the
 * acceptance-test suite relies on to run the whole saga in a single JVM.
 */
public final class EventflowApplication {

    private EventflowApplication() {
    }

    public static ConfigurableApplicationContext run(Class<?> applicationClass, String serviceName, String... args) {
        return builder(applicationClass, serviceName).run(args);
    }

    public static SpringApplicationBuilder builder(Class<?> applicationClass, String serviceName) {
        return new SpringApplicationBuilder(applicationClass).properties("spring.config.name=" + serviceName);
    }
}

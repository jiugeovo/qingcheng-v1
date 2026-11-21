package com.example.ordering.couple;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Checks storage targets before a DataSource or Flyway can open them. */
public class CoupleEnvironment implements EnvironmentPostProcessor, Ordered {
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        var mode = CoupleAccess.Mode.valueOf(environment.getProperty("app.couple.mode", "LEGACY").toUpperCase(Locale.ROOT));
        if (mode == CoupleAccess.Mode.LEGACY) return;
        boolean test = mode == CoupleAccess.Mode.TEST;
        String database = test ? "qingcheng_ordering_v1_test" : "qingcheng_ordering_v1";
        if (environment.containsProperty("spring.flyway.url") || environment.containsProperty("spring.flyway.user")) {
            throw new IllegalStateException("Flyway must use the 1.0 instance DataSource");
        }
        if (environment.containsProperty("spring.datasource.hikari.jdbc-url")
                || environment.containsProperty("spring.datasource.hikari.data-source-class-name")
                || environment.containsProperty("spring.datasource.jndi-name")) {
            throw new IllegalStateException("Configure the 1.0 database with spring.datasource.url");
        }
        String url = environment.getRequiredProperty("spring.datasource.url");
        String mysql = "jdbc:mysql://(?:127\\.0\\.0\\.1|localhost)(?::[0-9]+)?/" + database + "(?:\\?[^#]*)?";
        String local = "jdbc:h2:mem:" + database + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        if (!Pattern.matches(mysql, url) && !local.equals(url)) {
            throw new IllegalStateException("The 1.0 instance requires its own " + database + " database");
        }
        checkUploads(environment.getRequiredProperty("app.upload.dir"), test ? "test-uploads" : "production-uploads");
        if (test || local.equals(url)) {
            environment.getPropertySources().addFirst(new MapPropertySource("couple-offline", Map.of(
                    "server.address", "127.0.0.1",
                    "app.couple.offline-integrations", "true",
                    "app.push.enabled", "false",
                    "app.birthday.enabled", "false",
                    "app.ai.api-key", "",
                    "app.weather.qweather-key", "",
                    "app.legacy-schema-migration.enabled", "false"
            )));
        }
    }

    private void checkUploads(String configured, String leaf) {
        Path path = Path.of(configured).normalize();
        try {
            if (path.isAbsolute() && path.getFileName() != null && leaf.equals(path.getFileName().toString())
                    && Files.isDirectory(path) && path.equals(path.toRealPath())) {
                return;
            }
        } catch (IOException ignored) {
            // Fall through to a configuration error without revealing filesystem details.
        }
        throw new IllegalStateException("The 1.0 instance requires its own existing " + leaf + " directory");
    }
}

package com.authvault.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.mock.env.MockEnvironment;

class DatabaseConfigurationTest {
    @TempDir Path directory;

    @Test
    void resolvesEnvironmentNamesAndIsolatedDefaults() throws Exception {
        Properties properties = new Properties();
        try (var input = new ClassPathResource("application.properties").getInputStream()) {
            properties.load(input);
        }
        // MockEnvironment intentionally does not import the developer's real environment.
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addLast(new PropertiesPropertySource("application", properties));
        assertThat(environment.getProperty("server.port")).isEqualTo("3000");
        assertThat(environment.getProperty("authvault.database-path")).isEqualTo("./data/authvault.sqlite");
        environment.setProperty("PORT", "3100");
        environment.setProperty("DATABASE_PATH", directory.resolve("override.sqlite").toString());
        assertThat(environment.getProperty("server.port")).isEqualTo("3100");
        assertThat(environment.getProperty("authvault.database-path")).isEqualTo(directory.resolve("override.sqlite").toString());
    }

    @Test
    void configuredPathCreatesParentsAndInitializesBeforeBeanIsAvailable() {
        Path database = directory.resolve("nested/path/configured.sqlite");
        new ApplicationContextRunner().withUserConfiguration(DatabaseConfiguration.class)
                .withPropertyValues("authvault.database-path=" + database)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(DataSource.class);
                    assertThat(database).exists();
                    try (var connection = context.getBean(DataSource.class).getConnection();
                         var statement = connection.createStatement();
                         var rows = statement.executeQuery("SELECT COUNT(*) FROM platform_settings")) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getInt(1)).isEqualTo(2);
                    }
                });
    }

    @Test
    void invalidDatabasePathFailsStartupInsteadOfServingAnUninitializedApi() throws Exception {
        Path file = directory.resolve("not-a-directory");
        Files.writeString(file, "existing file");
        new ApplicationContextRunner().withUserConfiguration(DatabaseConfiguration.class)
                .withPropertyValues("authvault.database-path=" + file.resolve("db.sqlite"))
                .run(context -> assertThat(context).hasFailed());
        assertThat(Files.readString(file)).isEqualTo("existing file");
    }
}

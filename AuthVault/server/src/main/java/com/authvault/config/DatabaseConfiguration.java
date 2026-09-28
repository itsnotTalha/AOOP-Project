package com.authvault.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import com.authvault.repository.SchemaInitializer;

@Configuration(proxyBeanMethods = false)
public class DatabaseConfiguration {
    @Bean
    DataSource dataSource(@Value("${authvault.database-path}") String databasePath)
            throws IOException, SQLException {
        SQLiteDataSource source = createDataSource(Path.of(databasePath));
        // Finish migrations before publishing the DataSource or starting the HTTP server.
        new SchemaInitializer().initialize(source);
        return source;
    }

    public static SQLiteDataSource createDataSource(Path databasePath) throws IOException {
        Path absolutePath = databasePath.toAbsolutePath().normalize();
        Files.createDirectories(absolutePath.getParent());
        SQLiteConfig config = new SQLiteConfig();
        // Driver properties are applied to EVERY newly opened physical connection.
        config.enforceForeignKeys(true);
        SQLiteDataSource source = new SQLiteDataSource(config);
        source.setUrl("jdbc:sqlite:" + absolutePath);
        return source;
    }
}

package com.videoanalytics.db;

import com.videoanalytics.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

public class DatabaseManager {
    private final HikariDataSource dataSource;

    public DatabaseManager(AppConfig config) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.getDbUrl());
        hc.setUsername(config.getDbUser());
        hc.setPassword(config.getDbPassword());
        hc.setMaximumPoolSize(10);
        hc.setConnectionTimeout(30_000);
        try {
            this.dataSource = new HikariDataSource(hc);
        } catch (Exception e) {
            System.err.println("ERROR: Cannot connect to database: " + e.getMessage());
            System.exit(1);
            throw new RuntimeException(e);
        }
    }

    public void applySchema() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("schema.sql")) {
            if (is == null) throw new RuntimeException("schema.sql not found in classpath");
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        } catch (Exception e) {
            System.err.println("ERROR: Failed to apply schema: " + e.getMessage());
            System.exit(1);
        }
    }

    public DataSource getDataSource() {
        return dataSource;
    }
}

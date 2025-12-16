package com.yegnachat.server;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Scanner;

public class DatabaseService {
    private final HikariDataSource ds;

    public DatabaseService(String jdbcUrl, String username, String password) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(username);
        cfg.setPassword(password);
        cfg.setMaximumPoolSize(10);
        cfg.addDataSourceProperty("cachePrepStmts", "true");
        cfg.addDataSourceProperty("prepStmtCacheSize", "250");
        cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        this.ds = new HikariDataSource(cfg);


        runMigrationsIfNeeded();
    }

    public Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    public void close() {
        if (ds != null && !ds.isClosed()) ds.close();
    }

    private void runMigrationsIfNeeded() {
        try (InputStream in = getClass().getResourceAsStream("/db/schema.sql")) {
            if (in == null) {
                System.out.println("No bundled schema.sql found in resources/db/schema.sql — skipping migrations.");
                return;
            }
            String sql = new Scanner(in, StandardCharsets.UTF_8).useDelimiter("\\A").next();
            try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
                for (String stmt : sql.split(";")) {
                    String trimmed = stmt.trim();
                    if (trimmed.isEmpty()) continue;
                    st.execute(trimmed);
                }
            }
            System.out.println("Schema migration finished (schema.sql)");
        } catch (Exception e) {
            System.err.println("Failed running migrations: " + e.getMessage());

        }
    }
}

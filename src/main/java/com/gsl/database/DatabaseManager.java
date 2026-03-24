package com.gsl.database;

import com.gsl.config.ConfigFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin; // ✅ Added import

public class DatabaseManager {

    private static Jdbi jdbi;
    private static HikariDataSource dataSource;

    static {
        try {
            // Step 1: Configure HikariConfig
            HikariConfig config = new HikariConfig();

            var dbConfig = ConfigFactory.getDataBaseConfig(); // existing method

            String jdbcUrl = buildJdbcUrl(
                    dbConfig.databaseType(),
                    dbConfig.databaseHost(),
                    dbConfig.databasePort(),
                    dbConfig.databasename()
            );
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(dbConfig.databaseUsername());
            config.setPassword(dbConfig.databasePassword());

            // Optional tuning parameters
            config.setMaximumPoolSize(dbConfig.MaximumPoolSize());
            config.setMinimumIdle(dbConfig.MinimumIdle());
            config.setIdleTimeout(dbConfig.IdleTimeout());
            config.setConnectionTimeout(dbConfig.ConnectionTimeout());
            config.setLeakDetectionThreshold(dbConfig.LeakDetectionThreashold());

            // Set driver class
            switch (dbConfig.databaseType().toLowerCase()) {
                case "postgres" -> config.setDriverClassName("org.postgresql.Driver");
                case "oracle" -> config.setDriverClassName("oracle.jdbc.OracleDriver");
                case "sqlserver" -> config.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
                default -> throw new IllegalArgumentException("Unsupported database type: " + dbConfig.databaseType());
            }

            // Step 2: Initialize datasource and Jdbi
            dataSource = new HikariDataSource(config);
            jdbi = Jdbi.create(dataSource);

            // ✅ Step 3: Install plugin for DAO interface support
            jdbi.installPlugin(new SqlObjectPlugin());

            System.out.println("✅ DatabaseManager initialized successfully (JDBI + HikariCP)");
            System.out.println("Connected to: " + jdbcUrl);

            // ✅ Step 4: Log current schema for verification
            try {
                String currentSchema = jdbi.withHandle(handle ->
                        handle.createQuery("SELECT current_schema()")
                                .mapTo(String.class)
                                .one()
                );
                System.out.println("🟢 Current schema: " + currentSchema);

                String searchPath = jdbi.withHandle(handle ->
                        handle.createQuery("SHOW search_path")
                                .mapTo(String.class)
                                .one()
                );
                System.out.println("🔍 Search path: " + searchPath);

            } catch (Exception schemaEx) {
                System.err.println("⚠️ Could not determine current schema: " + schemaEx.getMessage());
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize DatabaseManager: " + e.getMessage());
        }
    }

    private static String buildJdbcUrl(String type, String host, String port, String name) {
        return switch (type.toLowerCase()) {
            case "postgres" -> "jdbc:postgresql://" + host + ":" + port + "/" + name;
            case "oracle" -> "jdbc:oracle:thin:@//" + host + ":" + port + "/" + name;
            case "sqlserver" -> "jdbc:sqlserver://" + host + ":" + port + ";databaseName=" + name;
            default -> throw new IllegalArgumentException("Unsupported database type: " + type);
        };
    }

    public static Jdbi getJdbi() {
        return jdbi;
    }

    public static HikariDataSource getDataSource() {
        return dataSource;
    }

    public static void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("Database connection closed.");
        }
    }
}

package com.gsl.database;

import com.gsl.config.ConfigFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin; // ✅ Added import

public class DatabaseManager {

    private DatabaseManager() {}

    private static class Holder {
        static final HikariDataSource DATA_SOURCE;
        static final Jdbi JDBI;

        static {
            try {
                HikariConfig config = new HikariConfig();
                var dbConfig = ConfigFactory.getDataBaseConfig();

                String jdbcUrl = buildJdbcUrl(
                        dbConfig.databaseType(),
                        dbConfig.databaseHost(),
                        dbConfig.databasePort(),
                        dbConfig.databasename()
                );
                config.setJdbcUrl(jdbcUrl);
                config.setUsername(dbConfig.databaseUsername());
                config.setPassword(dbConfig.databasePassword());

                config.setMaximumPoolSize(dbConfig.MaximumPoolSize());
                config.setMinimumIdle(dbConfig.MinimumIdle());
                config.setMaxLifetime(dbConfig.MaxLifetime());
                config.setIdleTimeout(dbConfig.IdleTimeout());
                config.setConnectionTimeout(dbConfig.ConnectionTimeout());
                config.setLeakDetectionThreshold(dbConfig.LeakDetectionThreashold());

                switch (dbConfig.databaseType().toLowerCase()) {
                    case "postgres" -> config.setDriverClassName("org.postgresql.Driver");
                    case "oracle" -> config.setDriverClassName("oracle.jdbc.OracleDriver");
                    case "sqlserver" -> config.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
                    default -> throw new IllegalArgumentException("Unsupported database type: " + dbConfig.databaseType());
                }

                DATA_SOURCE = new HikariDataSource(config);
                JDBI = Jdbi.create(DATA_SOURCE);
                JDBI.installPlugin(new SqlObjectPlugin());
                JDBI.setSqlLogger(new JdbiSqlLogger());

                System.out.println("DatabaseManager initialized successfully (JDBI + HikariCP)");
                System.out.println("Connected to: " + jdbcUrl);

                try {
                    String currentSchema = JDBI.withHandle(handle ->
                            handle.createQuery("SELECT current_schema()").mapTo(String.class).one());
                    System.out.println("Current schema: " + currentSchema);

                    String searchPath = JDBI.withHandle(handle ->
                            handle.createQuery("SHOW search_path").mapTo(String.class).one());
                    System.out.println("Search path: " + searchPath);
                } catch (Exception schemaEx) {
                    System.err.println("Could not determine current schema: " + schemaEx.getMessage());
                }

            } catch (Exception e) {
                var dbConfig = ConfigFactory.getDataBaseConfig();
                String diagMsg = String.format(
                        "%n[DatabaseManager] INITIALIZATION FAILED%n" +
                        "  Host     : %s%n" +
                        "  Port     : %s%n" +
                        "  Database : %s%n" +
                        "  User     : %s%n" +
                        "  Type     : %s%n" +
                        "  Root cause: %s%n" +
                        "Checklist:%n" +
                        "  1. Is the DB host reachable? Run: ping %s%n" +
                        "  2. Is PostgreSQL running on port %s?%n" +
                        "  3. Are credentials correct in databaseconfig.properties?%n" +
                        "  4. Is VPN/network access required to reach the DB host?%n" +
                        "  5. Is the database name '%s' correct?",
                        dbConfig.databaseHost(), dbConfig.databasePort(), dbConfig.databasename(),
                        dbConfig.databaseUsername(), dbConfig.databaseType(),
                        e.getMessage(),
                        dbConfig.databaseHost(), dbConfig.databasePort(), dbConfig.databasename()
                );
                throw new RuntimeException(diagMsg, e);
            }
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
        return Holder.JDBI;
    }

    public static HikariDataSource getDataSource() {
        return Holder.DATA_SOURCE;
    }

    public static void close() {
        if (Holder.DATA_SOURCE != null && !Holder.DATA_SOURCE.isClosed()) {
            Holder.DATA_SOURCE.close();
            System.out.println("Database connection closed.");
        }
    }
}

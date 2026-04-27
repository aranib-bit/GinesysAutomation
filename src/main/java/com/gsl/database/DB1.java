package com.gsl.database;

import com.gsl.config.ConfigFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jdbi.v3.core.Jdbi;

import static com.gsl.config.ConfigFactory.getDataBaseConfig;

public class DB1{

    private static Jdbi jdbi;
    private static HikariDataSource dataSource;


    static {
        try {
            //--Step 1 :Configure HikariConfig (Connection Pool)
            HikariConfig config = new HikariConfig();
            String url = ConfigFactory.getDataBaseConfig().databaseHost();
            System.out.println(url);
            String databasename = ConfigFactory.getDataBaseConfig().databasename();
            System.out.println(databasename);


            config.setJdbcUrl(
                    "jdbc:postgresql://"
                            + url + ":"
                            + ConfigFactory.getDataBaseConfig().databasePort()
                            + "/" + databasename
            );
            config.setUsername(ConfigFactory.getDataBaseConfig().databaseUsername());
            config.setPassword(ConfigFactory.getDataBaseConfig().databasePassword());

            //Optional tuning parameters (recommended for test automation)
            config.setMaximumPoolSize(ConfigFactory.getDataBaseConfig().MaximumPoolSize());
            config.setMinimumIdle(ConfigFactory.getDataBaseConfig().MinimumIdle());
            config.setMaxLifetime(ConfigFactory.getDataBaseConfig().MaxLifetime());
            config.setIdleTimeout(ConfigFactory.getDataBaseConfig().IdleTimeout());
            config.setConnectionTimeout(ConfigFactory.getDataBaseConfig().ConnectionTimeout());
            config.setLeakDetectionThreshold(ConfigFactory.getDataBaseConfig().LeakDetectionThreashold());

            //---Step 2:Create  Hikari DataSource
            dataSource = new HikariDataSource(config);

            //---Step 3: Initialize JDBI using Hikari DataSource
            jdbi = Jdbi.create(dataSource);

            System.out.println("DatabaseManager initialized successfully (JDBI + HikariCP)");
        } catch (Exception e){
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize DatabaseManager"+e.getMessage());
        }
    }
    //--Step 4:Public accessor
    public static Jdbi getJdbi(){
        return jdbi;
    }

    public static void close(){
        if (dataSource!=null &&  !dataSource.isClosed()){
            dataSource.close();
            System.out.println("Database Connection closed.");
        }
    }
}

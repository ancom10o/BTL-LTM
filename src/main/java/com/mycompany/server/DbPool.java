package com.mycompany.server;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

public class DbPool {
    private static HikariDataSource ds;

    public static DataSource get() {
        if (ds == null) {
            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl("jdbc:postgresql://localhost:5432/soundduel");
            cfg.setUsername("soundduel");
            cfg.setPassword("ha16092004");
            cfg.setMaximumPoolSize(10);
            cfg.setPoolName("ServerAuthPool");

            ds = new HikariDataSource(cfg);
            System.out.println("Connected to PostgreSQL via HikariCP!");
        }
        return ds;
    }
}

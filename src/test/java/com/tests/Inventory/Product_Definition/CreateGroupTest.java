package com.tests.Inventory.Product_Definition;

import com.gsl.database.DatabaseManager;
import org.jdbi.v3.core.Jdbi;
import java.util.List;
import java.util.Map;

public class CreateGroupTest {
    public static void main(String[] args) {
        try {
            // Step 1: Get Jdbi instance from DatabaseManager
            Jdbi jdbi = DatabaseManager.getJdbi();

            // Step 2: Define your query
            String sql = "SELECT grpname FROM invgrp";

            // Step 3: Execute query and fetch results as List of Maps
            List<Map<String, Object>> results = jdbi.withHandle(handle ->
                    handle.createQuery(sql).mapToMap().list()
            );

            // Step 4: Print results
            if (results.isEmpty()) {
                System.out.println("No rows found in invgrp with lev = 1");
            } else {
                results.forEach(row -> {
                    System.out.println("---- Row ----");
                    row.forEach((column, value) ->
                            System.out.println(column + " = " + value));
                });
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            DatabaseManager.close();
        }
    }
}

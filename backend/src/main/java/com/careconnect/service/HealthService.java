package com.careconnect.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class HealthService {

    private final JdbcTemplate jdbcTemplate;

    public HealthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, String> check() {
        String database;
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            database = (one != null && one == 1) ? "UP" : "DOWN";
        } catch (Exception e) {
            database = "DOWN";
        }
        return Map.of("app", "UP", "database", database);
    }
}

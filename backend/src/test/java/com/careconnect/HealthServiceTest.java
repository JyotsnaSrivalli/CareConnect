package com.careconnect;

import com.careconnect.service.HealthService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthServiceTest {

    @Test
    void reportsDatabaseUpWhenQuerySucceeds() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        assertEquals("UP", new HealthService(jdbc).check().get("database"));
    }

    @Test
    void reportsDatabaseDownWhenQueryFails() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenThrow(new RuntimeException("no db"));
        assertEquals("DOWN", new HealthService(jdbc).check().get("database"));
    }
}

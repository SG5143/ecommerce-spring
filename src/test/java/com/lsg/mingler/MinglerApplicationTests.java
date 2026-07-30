package com.lsg.mingler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MinglerApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void Flyway_V1부터_V5까지_결제스키마가_순서대로_적용된다() {
        Integer versionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version IN ('1', '2', '3', '4', '5') AND success = 1
                """, Integer.class);
        Integer tossColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'payment'
                  AND column_name IN ('pg_order_id', 'pg_payment_key', 'processing_at')
                """, Integer.class);

        assertThat(versionCount).isEqualTo(5);
        assertThat(tossColumnCount).isEqualTo(3);
    }

}

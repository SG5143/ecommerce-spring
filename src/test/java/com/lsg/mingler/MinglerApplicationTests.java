package com.lsg.mingler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MinglerApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void Flyway_V1부터_V6까지_운영스키마가_순서대로_적용된다() {
        Integer versionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version IN ('1', '2', '3', '4', '5', '6') AND success = 1
                """, Integer.class);
        Integer tossColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'payment'
                  AND column_name IN ('pg_order_id', 'pg_payment_key', 'processing_at')
                """, Integer.class);

        Integer expirationColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'orders'
                  AND column_name = 'expired_at'
                """, Integer.class);

        assertThat(versionCount).isEqualTo(6);
        assertThat(tossColumnCount).isEqualTo(3);
        assertThat(expirationColumnCount).isOne();
    }

    @Test
    void 헬스_엔드포인트는_인증없이_UP을_반환한다() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void 보호된_API는_인증없이_접근할수없다() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());
    }

}

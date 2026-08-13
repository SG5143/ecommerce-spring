package com.lsg.mingler.global.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentReconciliationSqlFilterTest {

    private final PaymentReconciliationSqlFilter filter = new PaymentReconciliationSqlFilter();
    private final Logger hibernateSqlLogger = (Logger) LoggerFactory.getLogger("org.hibernate.SQL");

    @Test
    void 결제_재조정_대상조회_SQL은_로그에서_제외한다() {
        String sql = """
                select p1_0.id,p1_0.status
                from payment p1_0
                where p1_0.status=? and p1_0.processing_at<?
                and p1_0.failure_code is null
                order by p1_0.processing_at limit ?
                """;

        FilterReply result = filter.decide(
                null, hibernateSqlLogger, Level.DEBUG, sql, null, null);

        assertThat(result).isEqualTo(FilterReply.DENY);
    }

    @Test
    void 다른_Hibernate_SQL은_기존대로_로그에_출력한다() {
        String sql = "select m1_0.id from member m1_0 where m1_0.id=?";

        FilterReply result = filter.decide(
                null, hibernateSqlLogger, Level.DEBUG, sql, null, null);

        assertThat(result).isEqualTo(FilterReply.NEUTRAL);
    }
}

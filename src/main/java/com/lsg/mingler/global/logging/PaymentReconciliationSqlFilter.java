package com.lsg.mingler.global.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import java.util.Locale;
import org.slf4j.Marker;

/**
 * 결제 재조정 스케줄러가 주기적으로 실행하는 대상 조회 SQL만 로그에서 제외
 */
public class PaymentReconciliationSqlFilter extends TurboFilter {

    private static final String HIBERNATE_SQL_LOGGER = "org.hibernate.SQL";

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable throwable) {

        // Hibernate SQL 로그인지 확인
        if (logger == null || !HIBERNATE_SQL_LOGGER.equals(logger.getName()) || format == null) {
            return FilterReply.NEUTRAL;
        }

        String sql = format.toLowerCase(Locale.ROOT);

        // 재조정 조회 SQL의 특징이 모두 포함됐는지 검사
        boolean reconciliationQuery = sql.contains("from payment ")
                && sql.contains(".status=?")
                && sql.contains(".processing_at<?")
                && sql.contains(".failure_code is null")
                && sql.contains("order by")
                && sql.contains(".processing_at")
                && sql.contains("limit ?");

        // 다른 SQL과 일반 애플리케이션 로그는 NEUTRAL로 통과
        return reconciliationQuery ? FilterReply.DENY : FilterReply.NEUTRAL;
    }
}

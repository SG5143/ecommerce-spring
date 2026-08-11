package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.entity.OrderItem;
import com.lsg.mingler.domain.product.dao.ProductOptionRepository;
import com.lsg.mingler.domain.product.dao.ProductRepository;
import com.lsg.mingler.domain.product.entity.Product;
import com.lsg.mingler.domain.product.entity.ProductOption;
import com.lsg.mingler.global.error.ConflictException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=10")
@ActiveProfiles("test")
class InventoryRaceConditionIntegrationTest {

    private static final int INITIAL_STOCK = 1;
    private static final int WORKER_COUNT = 5;
    private static final long LATCH_TIMEOUT_SECONDS = 5;
    private static final long FUTURE_TIMEOUT_SECONDS = 10;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductOptionRepository productOptionRepository;

    @Autowired
    private PaymentInventoryService inventoryService;

    private Long productId;
    private Long optionId;

    @BeforeEach
    void 테스트용_상품과_옵션을_생성한다() {
        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM category ORDER BY id LIMIT 1",
                Long.class);
        Product product = productRepository.saveAndFlush(Product.builder()
                .categoryId(categoryId)
                .name("재고 동시성 테스트 상품")
                .price(10_000)
                .build());
        ProductOption option = productOptionRepository.saveAndFlush(ProductOption.builder()
                .productId(product.getId())
                .name("재고 동시성 테스트 옵션")
                .stockQuantity(INITIAL_STOCK)
                .isActive(true)
                .isDefault(false)
                .build());

        productId = product.getId();
        optionId = option.getId();
    }

    @AfterEach
    void 테스트용_상품과_옵션을_정리한다() {
        if (optionId != null) {
            productOptionRepository.deleteById(optionId);
        }
        if (productId != null) {
            productRepository.deleteById(productId);
        }
    }

    @Test
    void 락이_없는_읽기_후_쓰기에서는_논리적_초과판매가_발생한다() throws Exception {
        CountDownLatch ready = new CountDownLatch(WORKER_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch stockRead = new CountDownLatch(WORKER_COUNT);
        CountDownLatch updateStart = new CountDownLatch(1);

        List<Boolean> results = executeConcurrently(() -> {
            ready.countDown();
            await(start);

            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            return transaction.execute(status -> {
                int observedStock = findStock();
                stockRead.countDown();
                await(updateStart);

                if (observedStock <= 0) {
                    return false;
                }
                jdbcTemplate.update(
                        "UPDATE product_option SET stock_quantity = ? WHERE id = ?",
                        observedStock - 1,
                        optionId);
                return true;
            });
        }, ready, start, stockRead, updateStart);

        long successCount = results.stream().filter(Boolean.TRUE::equals).count();

        assertThat(successCount).isEqualTo(WORKER_COUNT);
        assertThat(successCount).isGreaterThan(INITIAL_STOCK);
        assertThat(findStock()).isZero();
    }

    @Test
    void 비관적_락을_사용하면_초기_재고만큼만_예약에_성공한다() throws Exception {
        CountDownLatch ready = new CountDownLatch(WORKER_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        OrderItem orderItem = OrderItem.builder()
                .productId(productId)
                .productOptionId(optionId)
                .quantity(1)
                .build();

        List<Boolean> results = executeConcurrently(() -> {
            ready.countDown();
            await(start);

            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            try {
                transaction.executeWithoutResult(status -> inventoryService.reserve(List.of(orderItem)));
                return true;
            } catch (ConflictException expected) {
                return false;
            }
        }, ready, start);

        long successCount = results.stream().filter(Boolean.TRUE::equals).count();
        long rejectedCount = results.stream().filter(result -> !result).count();

        assertThat(successCount).isEqualTo(INITIAL_STOCK);
        assertThat(rejectedCount).isEqualTo(WORKER_COUNT - INITIAL_STOCK);
        assertThat(successCount + findStock()).isEqualTo(INITIAL_STOCK);
        assertThat(findStock()).isZero();
    }

    private List<Boolean> executeConcurrently(
            Callable<Boolean> task,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        return executeConcurrently(task, ready, start, null, null);
    }

    private List<Boolean> executeConcurrently(
            Callable<Boolean> task,
            CountDownLatch ready,
            CountDownLatch start,
        CountDownLatch phaseReady,
        CountDownLatch phaseStart) throws Exception {
        List<Future<Boolean>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(WORKER_COUNT);
        try {
            for (int worker = 0; worker < WORKER_COUNT; worker++) {
                futures.add(executor.submit(task));
            }

            assertThat(ready.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            if (phaseReady != null && phaseStart != null) {
                assertThat(phaseReady.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
                phaseStart.countDown();
            }

            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            start.countDown();
            if (phaseStart != null) {
                phaseStart.countDown();
            }
            executor.shutdownNow();
            executor.awaitTermination(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private int findStock() {
        Integer stock = jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM product_option WHERE id = ?",
                Integer.class,
                optionId);
        if (stock == null) {
            throw new IllegalStateException("테스트할 상품 옵션을 찾을 수 없습니다.");
        }
        return stock;
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 실행 대기시간을 초과했습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시 실행 대기가 중단되었습니다.", e);
        }
    }
}

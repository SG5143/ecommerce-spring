package com.lsg.mingler.domain.cart.service;

import com.lsg.mingler.domain.cart.dao.CartRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CartExpirationServiceTest {

    @Mock
    private CartRepository cartRepository;

    @InjectMocks
    private CartExpirationService cartExpirationService;

    @Test
    void 만료된_비회원_장바구니를_ID와_만료시각으로_조건부_삭제한다() {
        LocalDateTime now = LocalDateTime.now();

        cartExpirationService.deleteExpiredGuestCart(1L, now);

        verify(cartRepository).deleteExpiredGuestCartById(1L, now);
    }

    @Test
    void 만료_장바구니_삭제는_새_트랜잭션을_사용한다() throws NoSuchMethodException {
        Transactional transactional = CartExpirationService.class
                .getDeclaredMethod("deleteExpiredGuestCart", Long.class, LocalDateTime.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}

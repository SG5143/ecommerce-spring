package com.lsg.mingler.domain.cart.service;

import com.lsg.mingler.domain.cart.dao.CartRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartExpirationService {

    private final CartRepository cartRepository;

    /** 읽기 전용 장바구니 조회와 분리된 트랜잭션에서 만료된 비회원 장바구니만 삭제한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteExpiredGuestCart(Long cartId, LocalDateTime now) {
        cartRepository.deleteExpiredGuestCartById(cartId, now);
    }
}

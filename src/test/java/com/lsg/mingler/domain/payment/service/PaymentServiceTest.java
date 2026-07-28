package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.payment.dto.PaymentConfirmRequest;
import com.lsg.mingler.domain.payment.dto.PaymentConfirmResponse;
import com.lsg.mingler.global.error.AuthenticationException;
import com.lsg.mingler.global.error.DuplicateException;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRequestCoordinator requestCoordinator;

    @Mock
    private PaymentTransactionService transactionService;

    @Captor
    private ArgumentCaptor<Supplier<PaymentConfirmResponse>> actionCaptor;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void 회원_결제요청은_정규화한_값으로_멱등_조정기를_호출한다() {
        PaymentConfirmRequest request = new PaymentConfirmRequest(" ORD-1 ", 10_000, "card");
        when(requestCoordinator.coordinate(
                eq("MEMBER:7:key-1"),
                eq("ORD-1\n10000\nCARD"),
                any())).thenReturn(null);

        paymentService.confirm(7L, null, " key-1 ", request);

        verify(requestCoordinator).coordinate(
                eq("MEMBER:7:key-1"),
                eq("ORD-1\n10000\nCARD"),
                actionCaptor.capture());
        actionCaptor.getValue().get();
        verify(transactionService).confirm(
                eq(7L),
                eq(null),
                eq(new PaymentService.PaymentConfirmCommand("ORD-1", 10_000, "CARD", "key-1")));
    }

    @Test
    void 비회원_토큰이_없으면_결제를_거부한다() {
        PaymentConfirmRequest request = new PaymentConfirmRequest("ORD-1", 10_000, "CARD");

        assertThatThrownBy(() -> paymentService.confirm(null, null, "key-1", request))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("토큰");

        verify(requestCoordinator, never()).coordinate(any(), any(), any());
    }

    @Test
    void 지원하지_않는_결제수단은_거부한다() {
        PaymentConfirmRequest request = new PaymentConfirmRequest("ORD-1", 10_000, "TRANSFER");

        assertThatThrownBy(() -> paymentService.confirm(7L, null, "key-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CARD");

        verify(requestCoordinator, never()).coordinate(any(), any(), any());
    }

    @Test
    void DB_멱등성_제약_충돌은_중복요청으로_변환한다() {
        PaymentConfirmRequest request = new PaymentConfirmRequest("ORD-1", 10_000, "CARD");
        when(requestCoordinator.coordinate(any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("unique constraint"));

        assertThatThrownBy(() -> paymentService.confirm(7L, null, "key-1", request))
                .isInstanceOf(DuplicateException.class)
                .hasMessageContaining("이미 처리");
    }
}

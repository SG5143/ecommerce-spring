package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.entity.OrderStatus;
import com.lsg.mingler.domain.payment.dto.PaymentConfirmRequest;
import com.lsg.mingler.domain.payment.dto.PaymentConfirmResponse;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import com.lsg.mingler.global.error.AuthenticationException;
import com.lsg.mingler.global.error.DuplicateException;
import com.lsg.mingler.global.error.PaymentDeclinedException;
import com.lsg.mingler.global.util.HashUtils;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Mock
    private PaymentFailureTransactionService failureTransactionService;

    @Captor
    private ArgumentCaptor<Supplier<PaymentConfirmResponse>> actionCaptor;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void 회원_결제요청은_정규화한_값으로_멱등_조정기를_호출한다() {
        PaymentConfirmRequest request = new PaymentConfirmRequest(" ORD-1 ", 10_000, "card");
        String expectedFingerprint = HashUtils.sha256Hex("5:ORD-15:100004:CARD7:SUCCESS");
        when(requestCoordinator.coordinate(
                eq("MEMBER:7:key-1"),
                eq(expectedFingerprint),
                any())).thenReturn(null);

        paymentService.confirm(7L, null, " key-1 ", null, request);

        verify(requestCoordinator).coordinate(
                eq("MEMBER:7:key-1"),
                eq(expectedFingerprint),
                actionCaptor.capture());
        actionCaptor.getValue().get();
        verify(transactionService).confirm(
                eq(7L),
                eq(null),
                eq(new PaymentService.PaymentConfirmCommand(
                        "ORD-1",
                        10_000,
                        "CARD",
                        "key-1",
                        VirtualPaymentScenario.SUCCESS)));
    }

    @Test
    void 비회원_토큰이_없으면_결제를_거부한다() {
        PaymentConfirmRequest request = new PaymentConfirmRequest("ORD-1", 10_000, "CARD");

        assertThatThrownBy(() -> paymentService.confirm(null, null, "key-1", null, request))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("토큰");

        verify(requestCoordinator, never()).coordinate(any(), any(), any());
    }

    @Test
    void 지원하지_않는_결제수단은_거부한다() {
        PaymentConfirmRequest request = new PaymentConfirmRequest("ORD-1", 10_000, "TRANSFER");

        assertThatThrownBy(() -> paymentService.confirm(7L, null, "key-1", null, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CARD");

        verify(requestCoordinator, never()).coordinate(any(), any(), any());
    }

    @Test
    void DB_멱등성_제약_충돌은_중복요청으로_변환한다() {
        PaymentConfirmRequest request = new PaymentConfirmRequest("ORD-1", 10_000, "CARD");
        when(requestCoordinator.coordinate(any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("unique constraint"));

        assertThatThrownBy(() -> paymentService.confirm(7L, null, "key-1", null, request))
                .isInstanceOf(DuplicateException.class)
                .hasMessageContaining("이미 처리");
    }

    @Test
    void 가상결제_시나리오는_정규화되어_멱등요청_지문에_포함된다() {
        PaymentConfirmRequest request = new PaymentConfirmRequest("ORD-1", 10_000, "CARD");
        String expectedFingerprint = HashUtils.sha256Hex("5:ORD-15:100004:CARD7:DELAYED");
        when(requestCoordinator.coordinate(
                eq("MEMBER:7:key-1"),
                eq(expectedFingerprint),
                any())).thenReturn(null);

        paymentService.confirm(7L, null, "key-1", " delayed ", request);

        verify(requestCoordinator).coordinate(
                eq("MEMBER:7:key-1"),
                eq(expectedFingerprint),
                any());
    }

    @Test
    void 줄바꿈이_포함된_값도_길이접두사로_경계를_보존해_지문을_생성한다() {
        PaymentConfirmRequest request = new PaymentConfirmRequest("ORD-\n1", 10_000, "CARD");
        String expectedFingerprint = HashUtils.sha256Hex("6:ORD-\n15:100004:CARD7:SUCCESS");
        when(requestCoordinator.coordinate(
                eq("MEMBER:7:key-1"),
                eq(expectedFingerprint),
                any())).thenReturn(null);

        paymentService.confirm(7L, null, "key-1", null, request);

        verify(requestCoordinator).coordinate(
                eq("MEMBER:7:key-1"),
                eq(expectedFingerprint),
                any());
    }

    @Test
    void 지원하지_않는_가상결제_시나리오는_거부한다() {
        PaymentConfirmRequest request = new PaymentConfirmRequest("ORD-1", 10_000, "CARD");

        assertThatThrownBy(() -> paymentService.confirm(
                7L, null, "key-1", "UNKNOWN", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SUCCESS, FAILED, DELAYED");

        verify(requestCoordinator, never()).coordinate(any(), any(), any());
    }

    @Test
    void 승인거절은_롤백된_뒤_별도_실패이력을_기록하고_원래예외를_유지한다() {
        PaymentConfirmRequest request = new PaymentConfirmRequest("ORD-1", 10_000, "CARD");
        PaymentDeclinedException failure = new PaymentDeclinedException();
        when(requestCoordinator.coordinate(any(), any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<PaymentConfirmResponse> action = invocation.getArgument(2);
            return action.get();
        });
        when(transactionService.confirm(any(), any(), any())).thenThrow(failure);

        assertThatThrownBy(() -> paymentService.confirm(
                7L, null, "key-1", "FAILED", request))
                .isSameAs(failure);

        verify(failureTransactionService).recordFailure(
                eq(7L),
                eq(null),
                eq(new PaymentService.PaymentConfirmCommand(
                        "ORD-1",
                        10_000,
                        "CARD",
                        "key-1",
                        VirtualPaymentScenario.FAILED)),
                eq(failure));
    }

    @Test
    void 실패기록_경합에서_기존_성공결제를_발견하면_성공응답으로_복구한다() {
        PaymentConfirmRequest request = new PaymentConfirmRequest("ORD-1", 10_000, "CARD");
        PaymentDeclinedException failure = new PaymentDeclinedException();
        PaymentConfirmResponse existingSuccess = successfulResponse();
        when(requestCoordinator.coordinate(any(), any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<PaymentConfirmResponse> action = invocation.getArgument(2);
            return action.get();
        });
        when(transactionService.confirm(any(), any(), any())).thenThrow(failure);
        when(failureTransactionService.recordFailure(any(), any(), any(), any()))
                .thenReturn(Optional.of(existingSuccess));

        PaymentConfirmResponse response = paymentService.confirm(
                7L, null, "key-1", "FAILED", request);

        assertThat(response).isEqualTo(existingSuccess);
    }

    @Test
    void UNIQUE제약_경합후_기존_성공결제를_재조회해_성공응답으로_복구한다() {
        PaymentConfirmRequest request = new PaymentConfirmRequest("ORD-1", 10_000, "CARD");
        PaymentDeclinedException failure = new PaymentDeclinedException();
        PaymentConfirmResponse existingSuccess = successfulResponse();
        when(requestCoordinator.coordinate(any(), any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<PaymentConfirmResponse> action = invocation.getArgument(2);
            return action.get();
        });
        when(transactionService.confirm(any(), any(), any())).thenThrow(failure);
        when(failureTransactionService.recordFailure(any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("audit"));
        when(failureTransactionService.resolveExistingPayment(any(), any(), any(), any()))
                .thenReturn(Optional.of(existingSuccess));

        PaymentConfirmResponse response = paymentService.confirm(
                7L, null, "key-1", "FAILED", request);

        assertThat(response).isEqualTo(existingSuccess);
        verify(failureTransactionService).resolveExistingPayment(any(), any(), any(), any());
    }

    @Test
    void 실패이력_경합결과를_확인하지_못해도_원래_승인거절을_유지한다() {
        PaymentConfirmRequest request = new PaymentConfirmRequest("ORD-1", 10_000, "CARD");
        PaymentDeclinedException failure = new PaymentDeclinedException();
        when(requestCoordinator.coordinate(any(), any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<PaymentConfirmResponse> action = invocation.getArgument(2);
            return action.get();
        });
        when(transactionService.confirm(any(), any(), any())).thenThrow(failure);
        when(failureTransactionService.recordFailure(any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("audit"));
        when(failureTransactionService.resolveExistingPayment(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("기존 결제 없음"));

        assertThatThrownBy(() -> paymentService.confirm(
                7L, null, "key-1", "FAILED", request))
                .isSameAs(failure);
    }

    @Test
    void 실패기록_경합에서_기존요청이_다르면_중복요청을_그대로_반환한다() {
        PaymentConfirmRequest request = new PaymentConfirmRequest("ORD-1", 10_000, "CARD");
        PaymentDeclinedException failure = new PaymentDeclinedException();
        DuplicateException duplicate = new DuplicateException("기존 요청과 다름");
        when(requestCoordinator.coordinate(any(), any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<PaymentConfirmResponse> action = invocation.getArgument(2);
            return action.get();
        });
        when(transactionService.confirm(any(), any(), any())).thenThrow(failure);
        when(failureTransactionService.recordFailure(any(), any(), any(), any()))
                .thenThrow(duplicate);

        assertThatThrownBy(() -> paymentService.confirm(
                7L, null, "key-1", "FAILED", request))
                .isSameAs(duplicate);
    }

    @Test
    void PG결과가_확정되지_않은_시스템오류는_실패결제로_기록하지_않는다() {
        PaymentConfirmRequest request = new PaymentConfirmRequest("ORD-1", 10_000, "CARD");
        IllegalStateException systemFailure = new IllegalStateException("DB 오류");
        when(requestCoordinator.coordinate(any(), any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<PaymentConfirmResponse> action = invocation.getArgument(2);
            return action.get();
        });
        when(transactionService.confirm(any(), any(), any())).thenThrow(systemFailure);

        assertThatThrownBy(() -> paymentService.confirm(
                7L, null, "key-1", "SUCCESS", request))
                .isSameAs(systemFailure);

        verify(failureTransactionService, never()).recordFailure(any(), any(), any(), any());
    }

    private PaymentConfirmResponse successfulResponse() {
        return new PaymentConfirmResponse(
                "PAY-SUCCESS",
                "ORD-1",
                PaymentStatus.SUCCESS,
                OrderStatus.PAID,
                10_000,
                "CARD",
                "VIRTUAL",
                "VPG-SUCCESS",
                LocalDateTime.of(2026, 7, 29, 15, 0));
    }
}

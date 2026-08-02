package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.payment.dto.PaymentPrepareRequest;
import com.lsg.mingler.domain.payment.dto.PaymentPrepareResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentPreparationServiceTest {

    @Mock
    private PaymentPreparationTransactionService transactionService;
    @Mock
    private PaymentGatewayResolver gatewayResolver;

    @InjectMocks
    private PaymentPreparationService service;

    @Test
    void 선택한_결제제공자를_검증해_결제준비에_전달한다() {
        PaymentPrepareRequest request = new PaymentPrepareRequest("ORD-1", "CARD", "virtual");
        PaymentPrepareResponse expected = new PaymentPrepareResponse(
                null,
                "VIRTUAL-order",
                "상품",
                10_000,
                "KRW",
                "customer-key",
                "VIRTUAL");
        when(gatewayResolver.resolveAvailableProvider("virtual")).thenReturn("VIRTUAL");
        when(transactionService.prepare(
                7L, null, "ORD-1", "CARD", "VIRTUAL", "key-1"))
                .thenReturn(expected);

        PaymentPrepareResponse response = service.prepare(7L, null, "key-1", request);

        assertThat(response).isEqualTo(expected);
        verify(transactionService).prepare(
                7L, null, "ORD-1", "CARD", "VIRTUAL", "key-1");
    }

    @Test
    void 요청에_제공자가_없으면_설정된_기본제공자를_사용한다() {
        PaymentPrepareRequest request = new PaymentPrepareRequest("ORD-1", "CARD");
        when(gatewayResolver.resolveAvailableProvider(null)).thenReturn("TOSS");

        service.prepare(7L, null, "key-1", request);

        verify(transactionService).prepare(
                7L, null, "ORD-1", "CARD", "TOSS", "key-1");
    }

    @Test
    void 사용할수없는_결제제공자는_준비전에_거부한다() {
        PaymentPrepareRequest request = new PaymentPrepareRequest("ORD-1", "CARD", "TOSS");
        when(gatewayResolver.resolveAvailableProvider("TOSS"))
                .thenThrow(new IllegalArgumentException("현재 사용할 수 없는 결제 제공사입니다: TOSS"));

        assertThatThrownBy(() -> service.prepare(7L, null, "key-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("현재 사용할 수 없는");
    }
}

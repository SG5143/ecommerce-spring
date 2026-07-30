package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.global.error.PaymentApprovalTimeoutException;
import com.lsg.mingler.global.error.PaymentDeclinedException;
import com.lsg.mingler.global.error.PaymentGatewayNotFoundException;
import com.lsg.mingler.global.error.PaymentGatewayUncertainException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 외부 PG 호출 없이 결제 승인과 조회 결과를 메모리에서 재현하는 가상 결제 게이트웨이다.
 * 로컬 개발과 자동화 테스트에서 성공·거절·결과 불명 시나리오를 검증하는 데 사용하며,
 * 저장된 결과는 애플리케이션 재시작 시 유지되지 않는다.
 */
@Component
public class VirtualPaymentGateway implements PaymentGateway {

    private final Map<String, PaymentGatewayResult> paymentsByKey = new ConcurrentHashMap<>();
    private final Map<String, PaymentGatewayResult> paymentsByOrder = new ConcurrentHashMap<>();

    /**
     * 가상 결제 구현체를 식별하는 제공자 코드를 반환한다.
     *
     * @return 가상 결제 제공자 코드
     */
    @Override
    public String provider() {
        return "VIRTUAL";
    }

    /**
     * 요청에 포함된 가상 결제 시나리오에 따라 승인 결과를 생성하고 조회용 메모리에 저장한다.
     * 시나리오가 없으면 성공으로 처리하며, 거절은 확정 실패로, 지연은 결과 불명 상태로 전달한다.
     *
     * @param command 결제 키, PG 주문번호, 금액과 가상 시나리오를 포함한 승인 명령
     * @return 승인에 성공한 가상 결제 결과
     * @throws PaymentDeclinedException 결제 거절 시나리오인 경우
     * @throws PaymentGatewayUncertainException 승인 결과 지연 시나리오인 경우
     */
    @Override
    public PaymentGatewayResult confirm(PaymentGatewayCommand command) {
        VirtualPaymentScenario scenario = command.virtualScenario() == null
                ? VirtualPaymentScenario.SUCCESS
                : command.virtualScenario();
        if (scenario == VirtualPaymentScenario.FAILED) {
            PaymentGatewayResult failed = result(command, "ABORTED", null,
                    PaymentDeclinedException.FAILURE_CODE, "가상 결제 승인이 거절되었습니다.");
            remember(failed);
            throw new PaymentDeclinedException();
        }
        if (scenario == VirtualPaymentScenario.DELAYED) {
            PaymentGatewayResult processing = result(command, "IN_PROGRESS", null, null, null);
            remember(processing);
            throw new PaymentGatewayUncertainException(
                    PaymentApprovalTimeoutException.FAILURE_CODE,
                    "가상 결제 승인 결과를 확인하지 못했습니다.");
        }

        PaymentGatewayResult result = result(
                command,
                "DONE",
                "VPG-" + command.orderId(),
                null,
                null);
        remember(result);
        return result;
    }

    /**
     * 결제 키로 이전에 생성한 가상 결제 결과를 조회한다.
     *
     * @param paymentKey 조회할 PG 결제 키
     * @return 저장된 가상 결제 결과
     * @throws PaymentGatewayNotFoundException 해당 결제 키로 저장된 결과가 없는 경우
     */
    @Override
    public PaymentGatewayResult lookupByPaymentKey(String paymentKey) {
        PaymentGatewayResult result = paymentsByKey.get(paymentKey);
        if (result == null) {
            throw new PaymentGatewayNotFoundException("가상 결제 정보를 찾을 수 없습니다.");
        }
        return result;
    }

    /**
     * PG 주문번호로 이전에 생성한 가상 결제 결과를 조회한다.
     *
     * @param orderId 조회할 PG 주문번호
     * @return 저장된 가상 결제 결과
     * @throws PaymentGatewayNotFoundException 해당 주문번호로 저장된 결과가 없는 경우
     */
    @Override
    public PaymentGatewayResult lookupByOrderId(String orderId) {
        PaymentGatewayResult result = paymentsByOrder.get(orderId);
        if (result == null) {
            throw new PaymentGatewayNotFoundException("가상 결제 정보를 찾을 수 없습니다.");
        }
        return result;
    }

    /**
     * 요청한 가상 시나리오에 따라 승인 성공, 승인 거절 또는 타임아웃을 재현한다.
     * 지연 시나리오는 요청 스레드를 점유하지 않고 즉시 타임아웃으로 처리한다.
     *
     * @param scenario 실행할 가상 결제 시나리오
     */
    public void approve(VirtualPaymentScenario scenario) {
        switch (scenario) {
            case SUCCESS -> {}
            case FAILED -> throw new PaymentDeclinedException();
            case DELAYED -> throw new PaymentApprovalTimeoutException();
        }
    }

    private PaymentGatewayResult result(
            PaymentGatewayCommand command,
            String status,
            String transactionKey,
            String failureCode,
            String failureMessage) {
        return new PaymentGatewayResult(
                command.paymentKey(),
                command.orderId(),
                command.amount(),
                status,
                "카드",
                transactionKey,
                "DONE".equals(status) ? OffsetDateTime.now() : null,
                failureCode,
                failureMessage);
    }

    private void remember(PaymentGatewayResult result) {
        paymentsByKey.put(result.paymentKey(), result);
        paymentsByOrder.put(result.orderId(), result);
    }
}

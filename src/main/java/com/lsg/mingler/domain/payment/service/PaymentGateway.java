package com.lsg.mingler.domain.payment.service;

/**
 * 결제 제공사별 승인과 조회 기능을 추상화한 공통 게이트웨이 계약이다.
 * 서비스 계층은 이 인터페이스를 통해 가상 결제와 토스 결제를 동일한 흐름으로 처리한다.
 */
public interface PaymentGateway {

    /**
     * 게이트웨이 구현체를 식별하는 제공자 코드를 반환한다.
     *
     * @return 결제 제공자 코드
     */
    String provider();

    /**
     * PG에 결제 승인을 요청한다.
     *
     * @param command 승인에 필요한 결제 명령
     * @return PG가 반환한 결제 결과
     */
    PaymentGatewayResult confirm(PaymentGatewayCommand command);

    /**
     * PG 결제 키로 현재 결제 결과를 조회한다.
     *
     * @param paymentKey PG가 발급한 결제 키
     * @return PG의 현재 결제 결과
     */
    PaymentGatewayResult lookupByPaymentKey(String paymentKey);

    /**
     * PG 주문번호로 현재 결제 결과를 조회한다.
     *
     * @param orderId 결제 준비 시 PG에 전달한 주문번호
     * @return PG의 현재 결제 결과
     */
    PaymentGatewayResult lookupByOrderId(String orderId);
}

package com.lsg.mingler.global.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 리소스_없음_예외는_404_응답으로_변환한다() {
        ResourceNotFoundException exception =
                new ResourceNotFoundException("주문을 찾을 수 없습니다.");

        ResponseEntity<ErrorResponse> response = handler.handleResourceNotFound(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("주문을 찾을 수 없습니다."));
    }

    @Test
    void 요청_충돌_예외는_409_응답으로_변환한다() {
        ConflictException exception = new ConflictException("재고가 부족합니다.");

        ResponseEntity<ErrorResponse> response = handler.handleConflict(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("재고가 부족합니다."));
    }

    @Test
    void 읽을수없는_요청본문은_400_응답으로_변환한다() {
        HttpMessageNotReadableException exception =
                new HttpMessageNotReadableException(
                        "요청 본문 변환 실패",
                        new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ErrorResponse> response = handler.handleHttpMessageNotReadable(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("요청 본문이 올바르지 않습니다."));
    }

    @Test
    void 결제승인_거절은_402_응답으로_변환한다() {
        PaymentDeclinedException exception = new PaymentDeclinedException();

        ResponseEntity<ErrorResponse> response = handler.handlePaymentDeclined(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse(exception.getMessage()));
    }

    @Test
    void 결제승인_지연은_504_응답으로_변환한다() {
        PaymentApprovalTimeoutException exception = new PaymentApprovalTimeoutException();

        ResponseEntity<ErrorResponse> response = handler.handlePaymentApprovalTimeout(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse(exception.getMessage()));
    }
}

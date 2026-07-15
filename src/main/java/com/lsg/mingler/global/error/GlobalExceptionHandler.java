package com.lsg.mingler.global.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 처리. 예외를 응답으로 변환하는 동시에 로깅 레벨을 직접 관리
 * - 예상 가능한 클라이언트 오류(검증 실패·중복·인증 실패)는 스택 트레이스 없이 INFO 로만 남김
 * - 예상하지 못한 예외만 ERROR + 스택 트레이스로 남겨 실제 장애를 구분
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.info("검증 실패 (400): {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(DuplicateException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateException e) {
        log.info("중복 요청 (409): {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e) {
        log.info("인증 실패 (401): {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(e.getMessage()));
    }

    /**
     * 내부 오류 메시지가 클라이언트에 노출되지 않도록 일반화된 문구로 응답
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("예상하지 못한 오류 (500)", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
    }

}

package com.lsg.mingler.global.error;

/**
 * 리소스의 현재 상태와 요청이 충돌해 작업을 완료할 수 없음을 나타내는 예외다.
 * {@link GlobalExceptionHandler}가 409 CONFLICT 응답으로 변환한다.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}

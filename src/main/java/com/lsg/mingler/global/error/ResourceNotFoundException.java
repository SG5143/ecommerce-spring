package com.lsg.mingler.global.error;

/**
 * 요청한 리소스가 없거나 요청자에게 공개할 수 없음을 나타내는 예외다.
 * {@link GlobalExceptionHandler}가 404 NOT FOUND 응답으로 변환한다.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}

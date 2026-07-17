package com.lsg.mingler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// JWT 자체 인증을 사용하므로 기본 인메모리 사용자(generated password) 자동 설정을 제외
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class MinglerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MinglerApplication.class, args);
    }

}

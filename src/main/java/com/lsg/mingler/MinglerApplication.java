package com.lsg.mingler;

import com.lsg.mingler.domain.payment.service.PaymentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

// JWT 자체 인증을 사용하므로 기본 인메모리 사용자(generated password) 자동 설정을 제외
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
@EnableConfigurationProperties(PaymentProperties.class)
public class MinglerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MinglerApplication.class, args);
    }

}

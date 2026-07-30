package com.lsg.mingler.global.config;

import com.lsg.mingler.global.jwt.JwtAuthenticationFilter;
import com.lsg.mingler.global.jwt.JwtProperties;
import com.lsg.mingler.global.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    public SecurityConfig(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // JWT Bearer 방식의 무상태 인증 (서버 세션 미사용)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // SSR 페이지 & 정적 리소스
                        .requestMatchers("/", "/login", "/signup", "/cart", "/checkout", "/checkout/**", "/myshop", "/myshop/**", "/categories/**", "/products/**", "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        // 회원 본인 정보(요약·상세)는 인증 필요
                        .requestMatchers("/api/v1/members/me", "/api/v1/members/me/**").authenticated()
                        // 인증 불필요 API: 회원가입/아이디 중복확인, 로그인/재발급/로그아웃
                        .requestMatchers("/api/v1/members/**").permitAll()
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/reissue", "/api/v1/auth/logout").permitAll()
                        // 장바구니는 회원·비회원 공용. 병합만 로그인 회원으로 제한
                        .requestMatchers(HttpMethod.POST, "/api/v1/cart/merge").authenticated()
                        .requestMatchers("/api/v1/cart", "/api/v1/cart/**").permitAll()
                        // 주문서 생성은 회원·비회원 공용
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders").permitAll()
                        // 결제 API는 회원 JWT 또는 비회원 주문 토큰으로 서비스 계층에서 소유권 확인
                        .requestMatchers("/api/v1/payments/**").permitAll()
                        // 그 외 API 는 인증 필요 (ADMIN 전용 경로는 향후 hasRole 규칙 추가 예정)
                        .anyRequest().authenticated())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // Bearer 헤더 기반 무상태 API + Refresh 쿠키는 SameSite=Strict 로 CSRF 를 완화하므로 csrf 비활성화
                .csrf(AbstractHttpConfigurer::disable)
                // 인증 실패(미인증 접근) 시 401 + ErrorResponse JSON 반환
                .exceptionHandling(handler -> handler.authenticationEntryPoint(unauthorizedEntryPoint()))
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 인증되지 않은 접근에 401 UNAUTHORIZED 와 ErrorResponse 형태의 JSON 을 응답하는 진입점
     */
    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"message\":\"인증이 필요합니다.\"}");
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

}

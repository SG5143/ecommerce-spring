package com.lsg.mingler.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(name = "phone_verified_at")
    private LocalDateTime phoneVerifiedAt;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_id", length = 100)
    private String providerId;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, length = 20)
    private String grade;

    @Column(name = "point_balance", nullable = false)
    private Integer pointBalance;

    @Column(name = "marketing_agreed", nullable = false)
    private Boolean marketingAgreed;

    @Column(name = "marketing_agreed_at")
    private LocalDateTime marketingAgreedAt;

    @Column(name = "terms_agreed_at", nullable = false)
    private LocalDateTime termsAgreedAt;

    @Column(name = "privacy_agreed_at", nullable = false)
    private LocalDateTime privacyAgreedAt;

    @Column(name = "login_fail_count", nullable = false)
    private Integer loginFailCount;

    @Column(name = "account_locked_until")
    private LocalDateTime accountLockedUntil;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Member(String username, String password, String name, String phone,
                   LocalDate birthDate, Boolean marketingAgreed, LocalDateTime marketingAgreedAt,
                   LocalDateTime termsAgreedAt, LocalDateTime privacyAgreedAt) {
        this.username = username;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.birthDate = birthDate;
        this.provider = "LOCAL";
        this.role = "USER";
        this.status = "ACTIVE";
        this.grade = "BRONZE";
        this.pointBalance = 0;
        this.loginFailCount = 0;
        this.marketingAgreed = marketingAgreed != null && marketingAgreed;
        this.marketingAgreedAt = marketingAgreedAt;
        this.termsAgreedAt = termsAgreedAt;
        this.privacyAgreedAt = privacyAgreedAt;
    }

    /** 계정이 현재 잠겨 있는지 여부 (잠금 해제 시각이 미래면 잠김) */
    public boolean isLocked() {
        return accountLockedUntil != null && accountLockedUntil.isAfter(LocalDateTime.now());
    }

    /** 회원 상태가 정상(ACTIVE)이라 로그인 가능한지 여부 */
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    /**
     * 로그인 실패를 1회 누적하고, 임계치 이상이면 지정 시간 동안 계정을 잠금
     * @param maxFailCount 잠금이 시작되는 실패 임계치
     * @param lockMinutes  잠금 지속 시간(분)
     */
    public void recordLoginFailure(int maxFailCount, long lockMinutes) {
        this.loginFailCount = (this.loginFailCount == null ? 0 : this.loginFailCount) + 1;
        if (this.loginFailCount >= maxFailCount) {
            this.accountLockedUntil = LocalDateTime.now().plusMinutes(lockMinutes);
        }
    }

    /** 로그인 성공 시 실패 카운트와 잠금 상태를 초기화 */
    public void resetLoginFailure() {
        this.loginFailCount = 0;
        this.accountLockedUntil = null;
    }

    /** 최근 로그인 시각을 현재 시각으로 갱신 */
    public void updateLastLoginAt() {
        this.lastLoginAt = LocalDateTime.now();
    }

}

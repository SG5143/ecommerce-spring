package com.lsg.mingler.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Refresh 토큰. 원문은 저장하지 않고 SHA-256 해시(token_hash)만 보관
 * 회전/로그아웃 시 revoke() 로 폐기하며, 폐기된 토큰이 재사용되면 탈취로 간주
 */
@Entity
@Table(name = "refresh_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private RefreshToken(Long memberId, String tokenHash, LocalDateTime expiresAt) {
        this.memberId = memberId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /**
     * 이미 폐기됐는지 여부 (회전·로그아웃으로 무효화된 토큰)
     */
    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * 만료 시각을 지났는지 여부
     */
    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }

    /**
     * 아직 사용 가능한 토큰인지 여부 (미폐기 + 미만료)
     */
    public boolean isUsable() {
        return !isRevoked() && !isExpired();
    }

    /**
     * 토큰을 폐기 처리 (현재 시각을 폐기 시각으로 기록).
     */
    public void revoke() {
        if (this.revokedAt == null) {
            this.revokedAt = LocalDateTime.now();
        }
    }

}

package com.lsg.mingler.domain.cart.dao;

import com.lsg.mingler.domain.cart.entity.Cart;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByMemberId(Long memberId);

    Optional<Cart> findByGuestTokenHash(String guestTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cart c WHERE c.memberId = :memberId")
    Optional<Cart> findByMemberIdForUpdate(@Param("memberId") Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cart c WHERE c.guestTokenHash = :guestTokenHash")
    Optional<Cart> findByGuestTokenHashForUpdate(@Param("guestTokenHash") String guestTokenHash);

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO cart (member_id, created_at, updated_at)
            VALUES (:memberId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, nativeQuery = true)
    int createMemberCartIfAbsent(@Param("memberId") Long memberId);

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO cart (guest_token_hash, expires_at, created_at, updated_at)
            VALUES (:guestTokenHash, :expiresAt, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, nativeQuery = true)
    int createGuestCartIfAbsent(@Param("guestTokenHash") String guestTokenHash,
                                @Param("expiresAt") LocalDateTime expiresAt);

    @Modifying
    @Query("DELETE FROM Cart c WHERE c.memberId IS NULL AND c.expiresAt <= :now")
    int deleteExpiredGuestCarts(@Param("now") LocalDateTime now);
}

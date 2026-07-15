package com.lsg.mingler.domain.member.dao;

import com.lsg.mingler.domain.member.entity.MemberAddress;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberAddressRepository extends JpaRepository<MemberAddress, Long> {

    Optional<MemberAddress> findByMemberIdAndIsDefaultTrue(Long memberId);

}

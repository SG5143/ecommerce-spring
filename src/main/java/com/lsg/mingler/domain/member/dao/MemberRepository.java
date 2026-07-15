package com.lsg.mingler.domain.member.dao;

import com.lsg.mingler.domain.member.entity.Member;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByUsername(String username);

    boolean existsByPhone(String phone);

    Optional<Member> findByUsername(String username);

}

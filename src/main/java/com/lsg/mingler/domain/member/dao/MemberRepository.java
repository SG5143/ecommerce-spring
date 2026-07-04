package com.lsg.mingler.domain.member.dao;

import com.lsg.mingler.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByUsername(String username);

}

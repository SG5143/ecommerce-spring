package com.lsg.mingler.domain.member.dao;

import com.lsg.mingler.domain.member.entity.MemberAddress;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberAddressRepository extends JpaRepository<MemberAddress, Long> {

}

package com.heihu.workorder.repository;

import com.heihu.workorder.model.SmsCode;
import java.time.LocalDateTime;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SmsCodeRepository extends JpaRepository<SmsCode, Long> {

    Optional<SmsCode> findByPhone(String phone);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SmsCode s where s.phone = :phone")
    Optional<SmsCode> findByPhoneForUpdate(@Param("phone") String phone);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update SmsCode s set s.verified = true where s.phone = :phone and s.code = :code "
            + "and s.verified = false and s.createdAt > :createdAfter")
    int consumeValidCode(@Param("phone") String phone, @Param("code") String code,
                         @Param("createdAfter") LocalDateTime createdAfter);

    void deleteByCreatedAtBefore(LocalDateTime createdBefore);
}

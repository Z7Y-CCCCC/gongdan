package com.heihu.workorder.repository;

import com.heihu.workorder.model.SmsCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmsCodeRepository extends JpaRepository<SmsCode, Long> {

    Optional<SmsCode> findByPhone(String phone);

    void deleteByCreatedAtBefore(LocalDateTime createdBefore);
}

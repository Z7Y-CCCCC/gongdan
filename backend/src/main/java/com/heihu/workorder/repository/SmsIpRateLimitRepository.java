package com.heihu.workorder.repository;

import com.heihu.workorder.model.SmsIpRateLimit;
import java.time.LocalDateTime;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SmsIpRateLimitRepository extends JpaRepository<SmsIpRateLimit, Long> {

    Optional<SmsIpRateLimit> findByClientIp(String clientIp);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rateLimit from SmsIpRateLimit rateLimit where rateLimit.clientIp = :clientIp")
    Optional<SmsIpRateLimit> findByClientIpForUpdate(@Param("clientIp") String clientIp);

    void deleteByWindowStartBefore(LocalDateTime windowStartBefore);
}

package com.heihu.workorder.repository;

import com.heihu.workorder.model.AuthAttempt;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthAttemptRepository extends JpaRepository<AuthAttempt, Long> {

    Optional<AuthAttempt> findByAttemptKey(String attemptKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AuthAttempt a where a.attemptKey = :attemptKey")
    Optional<AuthAttempt> findByAttemptKeyForUpdate(@Param("attemptKey") String attemptKey);

    void deleteByAttemptKey(String attemptKey);
}

package com.heihu.workorder.repository;

import com.heihu.workorder.model.AuthAttempt;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthAttemptRepository extends JpaRepository<AuthAttempt, Long> {

    Optional<AuthAttempt> findByAttemptKey(String attemptKey);

    void deleteByAttemptKey(String attemptKey);
}

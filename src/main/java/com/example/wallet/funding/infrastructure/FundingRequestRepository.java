package com.example.wallet.funding.infrastructure;

import com.example.wallet.funding.domain.FundingRequest;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.Optional;
import java.util.UUID;

public interface FundingRequestRepository extends JpaRepository<FundingRequest, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select r from FundingRequest r where r.id = :id")
    Optional<FundingRequest> findByIdForUpdate(UUID id);
}

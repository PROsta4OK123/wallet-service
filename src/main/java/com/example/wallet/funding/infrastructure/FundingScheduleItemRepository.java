package com.example.wallet.funding.infrastructure;

import com.example.wallet.funding.domain.FundingScheduleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FundingScheduleItemRepository extends JpaRepository<FundingScheduleItem, UUID> {
    Slice<FundingScheduleItem> findByFundingRequestIdOrderByDueDateAscIdAsc(UUID requestId, Pageable pageable);

    @Query("""
        select i from FundingScheduleItem i
        where i.fundingRequestId = :requestId
          and (i.dueDate > :afterDate or (i.dueDate = :afterDate and i.id > :afterId))
        order by i.dueDate, i.id
        """)
    Slice<FundingScheduleItem> findAfterCursor(UUID requestId, LocalDate afterDate,
                                                UUID afterId, Pageable pageable);

    @Query(value = """
        WITH candidates AS (
            SELECT i.id
            FROM funding_schedule_item i
            JOIN funding_request r ON r.id = i.funding_request_id
            LEFT JOIN payment p ON p.schedule_item_id = i.id
            WHERE i.due_date <= :today
              AND r.status IN ('APPROVED', 'PARTIALLY_PAID')
              AND p.id IS NULL
              AND (i.claimed_at IS NULL OR i.claimed_at < now() - interval '5 minutes')
            ORDER BY i.due_date, i.id
            FOR UPDATE OF i SKIP LOCKED
            LIMIT :batchSize
        )
        UPDATE funding_schedule_item i
           SET claim_token = :token, claimed_at = now()
          FROM candidates c
         WHERE i.id = c.id
        RETURNING i.*
        """, nativeQuery = true)
    List<FundingScheduleItem> claimDueItems(@Param("today") LocalDate today,
                                            @Param("batchSize") int batchSize,
                                            @Param("token") UUID token);

    @Modifying
    @Query("update FundingScheduleItem i set i.claimToken = null, i.claimedAt = null " +
           "where i.id = :id and i.claimToken = :token")
    int releaseClaim(UUID id, UUID token);

}

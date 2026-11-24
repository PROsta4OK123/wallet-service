package com.example.wallet.funding.application;

import com.example.wallet.funding.domain.FundingScheduleItem;
import com.example.wallet.funding.infrastructure.FundingScheduleItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ScheduleClaimService {
    private final FundingScheduleItemRepository items;

    public ScheduleClaimService(FundingScheduleItemRepository items) {
        this.items = items;
    }

    @Transactional
    public ClaimedBatch claim(LocalDate today, int batchSize) {
        UUID token = UUID.randomUUID();
        return new ClaimedBatch(token, items.claimDueItems(today, batchSize, token));
    }

    @Transactional
    public void release(UUID itemId, UUID token) {
        items.releaseClaim(itemId, token);
    }

    public record ClaimedBatch(UUID token, List<FundingScheduleItem> items) {}
}

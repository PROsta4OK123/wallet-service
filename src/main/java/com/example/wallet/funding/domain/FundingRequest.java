package com.example.wallet.funding.domain;

import com.example.wallet.shared.error.BusinessRuleException;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "funding_request")
public class FundingRequest {
    @Id private UUID id;
    private UUID sourceWalletId;
    private UUID targetWalletId;
    @Enumerated(EnumType.STRING) private FundingRequestStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private int totalItems;
    private int completedItems;
    @Version private long version;

    protected FundingRequest() {}

    public void ensurePaymentsAllowed() {
        if (status == FundingRequestStatus.SUSPENDED)
            throw new BusinessRuleException("FUNDING_REQUEST_SUSPENDED", "Funding request is suspended");
        if (status == FundingRequestStatus.FULLY_PAID)
            throw new BusinessRuleException("FUNDING_REQUEST_PAID", "Funding request is fully paid");
    }

    public void suspend(Instant now) {
        if (status == FundingRequestStatus.FULLY_PAID)
            throw new BusinessRuleException("FUNDING_REQUEST_PAID", "A fully paid request cannot be suspended");
        status = FundingRequestStatus.SUSPENDED;
        updatedAt = now;
    }

    public void recordCompletedPayment(Instant now) {
        if (completedItems >= totalItems) {
            throw new BusinessRuleException("FUNDING_PROGRESS_INVALID", "All schedule items are already paid");
        }
        completedItems++;
        status = completedItems == totalItems
            ? FundingRequestStatus.FULLY_PAID
            : FundingRequestStatus.PARTIALLY_PAID;
        updatedAt = now;
    }
    public UUID getId() { return id; }
    public UUID getSourceWalletId() { return sourceWalletId; }
    public UUID getTargetWalletId() { return targetWalletId; }
    public FundingRequestStatus getStatus() { return status; }
    public int getTotalItems() { return totalItems; }
    public int getCompletedItems() { return completedItems; }
}

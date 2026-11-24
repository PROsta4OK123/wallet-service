package com.example.wallet.funding.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "funding_schedule_item")
public class FundingScheduleItem {
    @Id private UUID id;
    private UUID fundingRequestId;
    private LocalDate dueDate;
    private BigDecimal amount;
    private Instant createdAt;
    private UUID claimToken;
    private Instant claimedAt;
    protected FundingScheduleItem() {}
    public UUID getId() { return id; }
    public UUID getFundingRequestId() { return fundingRequestId; }
    public LocalDate getDueDate() { return dueDate; }
    public BigDecimal getAmount() { return amount; }
}

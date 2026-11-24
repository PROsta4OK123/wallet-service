package com.example.wallet.funding.application;

import com.example.wallet.funding.domain.FundingRequest;
import com.example.wallet.funding.domain.FundingScheduleItem;
import com.example.wallet.funding.infrastructure.FundingRequestRepository;
import com.example.wallet.funding.infrastructure.FundingScheduleItemRepository;
import com.example.wallet.shared.error.ResourceNotFoundException;
import com.example.wallet.shared.error.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.util.UUID;
import java.time.LocalDate;

@Service
public class FundingRequestService {
    private final FundingRequestRepository requests;
    private final FundingScheduleItemRepository items;
    private final Clock clock;

    public FundingRequestService(FundingRequestRepository requests,
                                 FundingScheduleItemRepository items, Clock clock) {
        this.requests = requests;
        this.items = items;
        this.clock = clock;
    }

    @Transactional
    public FundingRequest suspend(UUID id) {
        FundingRequest request = requests.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        request.suspend(clock.instant());
        return request;
    }

    @Transactional(readOnly = true)
    public FundingRequestView get(UUID id, LocalDate afterDate, UUID afterId, int size) {
        FundingRequest request = requests.findById(id).orElseThrow(() -> notFound(id));
        int safeSize = Math.min(Math.max(size, 1), 200);
        if ((afterDate == null) != (afterId == null)) {
            throw new BusinessRuleException("INVALID_CURSOR",
                "Both afterDate and afterId must be provided as a cursor");
        }
        var limit = PageRequest.of(0, safeSize);
        Slice<FundingScheduleItem> schedule = afterDate == null
            ? items.findByFundingRequestIdOrderByDueDateAscIdAsc(id, limit)
            : items.findAfterCursor(id, afterDate, afterId, limit);
        return new FundingRequestView(request, schedule);
    }

    private ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("FUNDING_REQUEST_NOT_FOUND",
            "Funding request " + id + " was not found");
    }

    public record FundingRequestView(FundingRequest request, Slice<FundingScheduleItem> schedule) {}
}

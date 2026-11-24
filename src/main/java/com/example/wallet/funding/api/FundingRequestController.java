package com.example.wallet.funding.api;

import com.example.wallet.funding.application.FundingRequestService;
import com.example.wallet.funding.domain.FundingRequest;
import com.example.wallet.funding.domain.FundingRequestStatus;
import com.example.wallet.funding.domain.FundingScheduleItem;
import com.example.wallet.payment.api.PaymentController.PaymentResponse;
import com.example.wallet.payment.application.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/funding-requests")
@Tag(name = "Funding requests", description = "Immutable scheduled transfer plans")
public class FundingRequestController {
    private final FundingRequestService requests;
    private final PaymentService payments;

    public FundingRequestController(FundingRequestService requests, PaymentService payments) {
        this.requests = requests;
        this.payments = payments;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a funding request", description = "Schedule items use keyset pagination")
    FundingRequestResponse get(@PathVariable UUID id,
                               @RequestParam(required = false) LocalDate afterDate,
                               @RequestParam(required = false) UUID afterId,
                               @RequestParam(defaultValue = "50") int size) {
        return FundingRequestResponse.from(requests.get(id, afterDate, afterId, size));
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "Suspend a funding request")
    FundingRequestResponse suspend(@PathVariable UUID id) {
        requests.suspend(id);
        return FundingRequestResponse.from(requests.get(id, null, null, 50));
    }

    @PostMapping("/{requestId}/schedule-items/{itemId}/payment")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a payment for a schedule item", description = "May be called before the scheduled date")
    PaymentResponse createPayment(@PathVariable UUID requestId, @PathVariable UUID itemId) {
        return PaymentResponse.from(payments.createFundingPayment(requestId, itemId));
    }

    public record FundingRequestResponse(UUID id, UUID sourceWalletId, UUID targetWalletId,
                                         FundingRequestStatus status, int completedItems, int totalItems,
                                         List<ScheduleItemResponse> schedule, CursorMetadata cursor) {
        static FundingRequestResponse from(FundingRequestService.FundingRequestView view) {
            FundingRequest request = view.request();
            var content = view.schedule().getContent();
            FundingScheduleItem last = content.isEmpty() ? null : content.get(content.size() - 1);
            return new FundingRequestResponse(request.getId(), request.getSourceWalletId(),
                request.getTargetWalletId(), request.getStatus(), request.getCompletedItems(),
                request.getTotalItems(),
                content.stream().map(ScheduleItemResponse::from).toList(),
                new CursorMetadata(view.schedule().hasNext(),
                    view.schedule().hasNext() ? last.getDueDate() : null,
                    view.schedule().hasNext() ? last.getId() : null,
                    view.schedule().getSize()));
        }
    }

    public record CursorMetadata(boolean hasNext, LocalDate nextAfterDate,
                                 UUID nextAfterId, int size) {}

    public record ScheduleItemResponse(UUID id, LocalDate dueDate, BigDecimal amount) {
        static ScheduleItemResponse from(FundingScheduleItem item) {
            return new ScheduleItemResponse(item.getId(), item.getDueDate(), item.getAmount());
        }
    }
}

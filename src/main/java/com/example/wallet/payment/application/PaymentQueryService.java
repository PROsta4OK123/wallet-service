package com.example.wallet.payment.application;

import com.example.wallet.payment.domain.Payment;
import com.example.wallet.payment.infrastructure.PaymentRepository;
import com.example.wallet.shared.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentQueryService {
    private final PaymentRepository payments;

    public PaymentQueryService(PaymentRepository payments) {
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public Payment get(UUID id) {
        return payments.findById(id).orElseThrow(() ->
            new ResourceNotFoundException("PAYMENT_NOT_FOUND", "Payment " + id + " was not found"));
    }
}

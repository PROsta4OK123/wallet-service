package com.example.wallet.wallet.api;

import com.example.wallet.wallet.application.LedgerReconciliationService;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "ledgerReconciliation")
public class LedgerReconciliationEndpoint {
    private final LedgerReconciliationService reconciliation;

    public LedgerReconciliationEndpoint(LedgerReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @ReadOperation
    public LedgerReconciliationService.ReconciliationReport reconcile() {
        return reconciliation.verifyAll();
    }
}

package com.example.wallet.wallet.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class LedgerReconciliationService {
    private final JdbcTemplate jdbc;

    public LedgerReconciliationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public ReconciliationReport verifyAll() {
        List<WalletCheck> checks = jdbc.query("""
            SELECT w.id,
                   w.balance AS actual_balance,
                   w.opening_balance + COALESCE(SUM(
                       CASE e.direction
                           WHEN 'CREDIT' THEN e.amount
                           WHEN 'DEBIT' THEN -e.amount
                       END
                   ), 0) AS expected_balance
            FROM wallet w
            LEFT JOIN wallet_entry e ON e.wallet_id = w.id
            GROUP BY w.id, w.balance, w.opening_balance
            """, (rs, row) -> new WalletCheck(
                rs.getObject("id", UUID.class),
                rs.getBigDecimal("actual_balance"),
                rs.getBigDecimal("expected_balance")));

        long mismatches = checks.stream().filter(check -> !check.consistent()).count();
        return new ReconciliationReport(checks.size(), mismatches, mismatches == 0);
    }

    private record WalletCheck(UUID walletId, BigDecimal actual, BigDecimal expected) {
        boolean consistent() { return actual.compareTo(expected) == 0; }
    }

    public record ReconciliationReport(int checkedWallets, long mismatchCount, boolean consistent) {}
}

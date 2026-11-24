package com.example.wallet.payment.infrastructure;

import com.example.wallet.shared.error.BusinessRuleException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Repository
public class IdempotencyStore {
    private final JdbcTemplate jdbc;

    public IdempotencyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean reserve(String key, String fingerprint, Instant now) {
        int inserted = jdbc.update("""
            INSERT INTO idempotency_record(idempotency_key, request_fingerprint, created_at)
            VALUES (?, ?, ?)
            ON CONFLICT (idempotency_key) DO NOTHING
            """, key, fingerprint,
            new SqlParameterValue(Types.TIMESTAMP_WITH_TIMEZONE,
                OffsetDateTime.ofInstant(now, ZoneOffset.UTC)));
        return inserted == 1;
    }

    public ExistingRequest get(String key) {
        return jdbc.queryForObject("""
            SELECT request_fingerprint, payment_id
            FROM idempotency_record
            WHERE idempotency_key = ?
            """, (rs, row) -> new ExistingRequest(
                rs.getString("request_fingerprint"),
                rs.getObject("payment_id", UUID.class)), key);
    }

    public void attachPayment(String key, UUID paymentId) {
        int updated = jdbc.update("""
            UPDATE idempotency_record SET payment_id = ? WHERE idempotency_key = ?
            """, paymentId, key);
        if (updated != 1) {
            throw new BusinessRuleException("IDEMPOTENCY_STATE_ERROR", "Could not attach payment to idempotency key");
        }
    }

    public record ExistingRequest(String fingerprint, UUID paymentId) {}
}

-- Run after loading production-like cardinality, not only the three demo wallets.
-- psql -h localhost -p 5433 -U wallet -d wallet -f scripts/explain-hot-queries.sql

BEGIN;

EXPLAIN (ANALYZE, BUFFERS, WAL)
SELECT id, balance
FROM wallet
WHERE id IN (
    '10000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000002'
)
ORDER BY id
FOR UPDATE;

EXPLAIN (ANALYZE, BUFFERS)
SELECT id, funding_request_id, due_date, amount
FROM funding_schedule_item
WHERE funding_request_id = '20000000-0000-0000-0000-000000000001'
  AND (due_date, id) > (
      CURRENT_DATE - 1,
      '00000000-0000-0000-0000-000000000000'::uuid
  )
ORDER BY due_date, id
LIMIT 51;

EXPLAIN (ANALYZE, BUFFERS)
SELECT request_fingerprint, payment_id
FROM idempotency_record
WHERE idempotency_key = 'explain-only-key';

EXPLAIN (ANALYZE, BUFFERS)
SELECT w.id,
       w.balance,
       w.opening_balance + COALESCE(SUM(
           CASE e.direction WHEN 'CREDIT' THEN e.amount WHEN 'DEBIT' THEN -e.amount END
       ), 0) AS expected_balance
FROM wallet w
LEFT JOIN wallet_entry e ON e.wallet_id = w.id
GROUP BY w.id, w.balance, w.opening_balance;

ROLLBACK;

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SOURCE = '10000000-0000-0000-0000-000000000001';
const TARGET = '10000000-0000-0000-0000-000000000002';

const confirmationLatency = new Trend('payment_confirmation_latency', true);
const businessConflicts = new Counter('business_conflicts');

export const options = {
  scenarios: {
    transfers: {
      executor: 'constant-arrival-rate',
      rate: 20,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 20,
      maxVUs: 100,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    payment_confirmation_latency: ['p(95)<500'],
  },
};

export default function () {
  const key = `k6-${__VU}-${__ITER}`;
  const payload = JSON.stringify({
    sourceWalletId: SOURCE,
    targetWalletId: TARGET,
    amount: 0.01,
  });
  const create = http.post(`${BASE_URL}/api/v1/payments/transfers`, payload, {
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key },
  });
  if (create.status === 409) businessConflicts.add(1);
  check(create, { 'payment created': response => response.status === 201 });
  if (create.status !== 201) return;

  const paymentId = create.json('id');
  const confirm = http.post(`${BASE_URL}/api/v1/payments/${paymentId}/confirm`);
  confirmationLatency.add(confirm.timings.duration);
  if (confirm.status === 409) businessConflicts.add(1);
  check(confirm, { 'payment completed': response => response.status === 200 });
}

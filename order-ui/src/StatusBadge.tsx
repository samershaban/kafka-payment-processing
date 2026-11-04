import type { OrderStatus } from './api'

const LABELS: Record<OrderStatus, string> = {
  CREATED: 'Created',
  PAID: 'Paid',
  PAYMENT_FAILED: 'Payment failed',
}

export function StatusBadge({ status }: { status: OrderStatus }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{LABELS[status] ?? status}</span>
}

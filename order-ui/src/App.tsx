import { useCallback, useEffect, useState } from 'react'
import { ApiError, getOrder, listOrders, type Order } from './api'
import { OrderForm } from './OrderForm'
import { OrderList } from './OrderList'
import { StatusBadge } from './StatusBadge'

const POLL_MS = 500
const POLL_TIMEOUT_MS = 10_000

export default function App() {
  const [orders, setOrders] = useState<Order[]>([])
  const [loading, setLoading] = useState(false)
  const [listError, setListError] = useState<string | null>(null)
  const [latest, setLatest] = useState<Order | null>(null)
  const [timedOut, setTimedOut] = useState(false)

  const refresh = useCallback(async () => {
    setLoading(true)
    try {
      setOrders(await listOrders())
      setListError(null)
    } catch (err) {
      setListError(err instanceof ApiError ? err.message : 'Could not load orders.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  // After creating an order, poll it until the Payment Service's result arrives via Kafka.
  const latestId = latest?.id
  const waiting = latest?.status === 'CREATED'
  useEffect(() => {
    if (!latestId || !waiting) return
    const started = Date.now()
    const timer = setInterval(async () => {
      if (Date.now() - started > POLL_TIMEOUT_MS) {
        clearInterval(timer)
        setTimedOut(true)
        return
      }
      try {
        const order = await getOrder(latestId)
        if (order.status !== 'CREATED') {
          clearInterval(timer)
          setLatest(order)
          setOrders((prev) => prev.map((o) => (o.id === order.id ? order : o)))
        }
      } catch {
        // ignore one failed poll; the next tick tries again
      }
    }, POLL_MS)
    return () => clearInterval(timer)
  }, [latestId, waiting])

  function handleCreated(order: Order) {
    setTimedOut(false)
    setLatest(order)
    setOrders((prev) => [order, ...prev.filter((o) => o.id !== order.id)])
  }

  return (
    <main>
      <header>
        <h1>Orders</h1>
        <p className="muted">
          Place an order. The Order Service saves it and publishes it to Kafka, the Payment Service charges it, and the
          status updates here.
        </p>
      </header>

      <OrderForm onCreated={handleCreated} />

      {latest && (
        <section className="card latest" aria-live="polite">
          <div>
            <strong>{latest.item}</strong> × {latest.quantity} for {latest.customerId}
            <div className="muted small mono">{latest.id}</div>
          </div>
          <div className="latest-status">
            <StatusBadge status={latest.status} />
            {latest.status === 'CREATED' && !timedOut && <span className="muted small">Waiting for payment…</span>}
            {timedOut && latest.status === 'CREATED' && (
              <span className="muted small">No payment result yet. Is the Payment Service running?</span>
            )}
            {latest.statusReason && <span className="muted small">{latest.statusReason}</span>}
          </div>
        </section>
      )}

      <OrderList orders={orders} loading={loading} error={listError} onRefresh={refresh} />
    </main>
  )
}

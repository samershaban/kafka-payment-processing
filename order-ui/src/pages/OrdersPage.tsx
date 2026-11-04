import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { ApiError, listOrders, subscribeToOrders, type Order, type User } from '../api'
import { OrderForm } from '../components/OrderForm'
import { OrderList } from '../components/OrderList'

/** Replace the order if it's already in the list, otherwise add it at the front. */
function upsert(orders: Order[], order: Order): Order[] {
  return orders.some((o) => o.id === order.id)
    ? orders.map((o) => (o.id === order.id ? order : o))
    : [order, ...orders]
}

export function OrdersPage({ users, usersError }: { users: User[]; usersError: string | null }) {
  const [orders, setOrders] = useState<Order[]>([])
  const [loading, setLoading] = useState(false)
  const [listError, setListError] = useState<string | null>(null)

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

  // Live updates (Server-Sent Events) instead of polling: the Order Service pushes every new order and every
  // status change (CREATED -> PAID / PAYMENT_FAILED after the Payment Service's Kafka result).
  // The stream is open only while this page is shown; coming back reloads the list and reconnects.
  const [live, setLive] = useState(false)
  const wasDisconnected = useRef(false)
  useEffect(() => {
    return subscribeToOrders(
      (order) => setOrders((prev) => upsert(prev, order)),
      (connected) => {
        setLive(connected)
        if (!connected) {
          wasDisconnected.current = true
        } else if (wasDisconnected.current) {
          // Events sent while we were disconnected are lost, so reload the list once we're back
          wasDisconnected.current = false
          refresh()
        }
      },
    )
  }, [refresh])

  const userNames = useMemo(() => new Map(users.map((u) => [u.id, u.name])), [users])

  // Show the new order straight from the POST response; the stream sends the same order too (upsert dedupes)
  function handleCreated(order: Order) {
    setOrders((prev) => upsert(prev, order))
  }

  return (
    <>
      <div className="page-title">
        <h1>Orders</h1>
        <span className={`live ${live ? 'on' : 'off'}`} aria-live="polite">
          {live ? 'Live' : 'Reconnecting…'}
        </span>
      </div>

      <OrderForm users={users} usersError={usersError} onCreated={handleCreated} />

      <OrderList orders={orders} userNames={userNames} loading={loading} error={listError} onRefresh={refresh} />
    </>
  )
}

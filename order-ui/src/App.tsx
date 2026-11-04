import { useCallback, useEffect, useMemo, useState } from 'react'
import { ApiError, getOrder, listOrders, listUsers, type Order, type User } from './api'
import { OrderForm } from './components/OrderForm'
import { OrderList } from './components/OrderList'

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

  // Users: options for the form's Customer dropdown, and names for the orders table
  const [users, setUsers] = useState<User[]>([])
  const [usersError, setUsersError] = useState<string | null>(null)
  useEffect(() => {
    listUsers()
      .then((list) => {
        setUsers([...list].sort((a, b) => a.name.localeCompare(b.name)))
        setUsersError(list.length === 0 ? 'No users yet. Add one with POST /users to place orders.' : null)
      })
      .catch((err) => setUsersError(err instanceof ApiError ? err.message : 'Could not load users.'))
  }, [])
  const userNames = useMemo(() => new Map(users.map((u) => [u.id, u.name])), [users])

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
      </header>

      <OrderForm users={users} usersError={usersError} onCreated={handleCreated} />

      <OrderList orders={orders} userNames={userNames} loading={loading} error={listError} onRefresh={refresh} />
    </main>
  )
}

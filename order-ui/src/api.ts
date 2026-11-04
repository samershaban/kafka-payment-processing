// Mirrors CreateOrderRequest.java
export interface CreateOrderRequest {
  customerId: string
  item: string
  quantity: number
  totalPrice: number
}

export type OrderStatus = 'CREATED' | 'PAID' | 'PAYMENT_FAILED'

// Mirrors Order.java as serialized by Jackson
export interface Order {
  id: string
  customerId: string
  item: string
  quantity: number
  totalPrice: number
  status: OrderStatus
  statusReason: string | null
  createdAt: string
  updatedAt: string
}

// Mirrors UserResponse.java (users are stored in Postgres; Order.customerId = User.id)
export interface User {
  id: string
  name: string
  email: string
  company: string | null
  address: string | null
  timezone: string
  enrolled: boolean
}

/** Thrown for non-2xx responses; fieldErrors is filled for @Valid failures (400). */
export class ApiError extends Error {
  status: number
  fieldErrors: Record<string, string>

  constructor(message: string, status: number, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

/**
 * All API calls go to /api/... ; the Vite dev server strips /api and forwards to the Order Service (port 8081).
 * The prefix keeps API paths apart from page paths: /users is the Users page, /api/users is the API.
 */
const API = '/api'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response
  try {
    res = await fetch(API + path, init)
  } catch {
    throw new ApiError('Cannot reach the Order Service. Is it running on port 8081?', 0)
  }

  if (!res.ok) {
    // Spring Boot error body; with server.error.include-binding-errors=always it has
    // errors: [{ field, defaultMessage }, ...]
    const body = await res.json().catch(() => null)
    const fieldErrors: Record<string, string> = {}
    for (const e of body?.errors ?? []) {
      if (e.field) fieldErrors[e.field] = e.defaultMessage
    }
    // body.message is the reason given by the server (server.error.include-message=always),
    // e.g. for 409/422 idempotency errors
    const message =
      res.status === 400 && Object.keys(fieldErrors).length > 0
        ? 'The server rejected the request.'
        : body?.message || `Request failed (${res.status} ${body?.error ?? res.statusText})`
    throw new ApiError(message, res.status, fieldErrors)
  }
  return res.json() as Promise<T>
}

export function createOrder(req: CreateOrderRequest, idempotencyKey: string): Promise<Order> {
  return request<Order>('/orders', {
    method: 'POST',
    // Same key + same body = the server returns the original order instead of creating a duplicate
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(req),
  })
}

export function getOrder(id: string): Promise<Order> {
  return request<Order>(`/orders/${encodeURIComponent(id)}`)
}

export function listOrders(): Promise<Order[]> {
  return request<Order[]>('/orders')
}

export function listUsers(): Promise<User[]> {
  return request<User[]>('/users')
}

// Mirrors UpdateUserRequest.java: PUT replaces the whole user, so every field is sent
export type UpdateUserRequest = Omit<User, 'id'>

export function updateUser(id: string, req: UpdateUserRequest): Promise<User> {
  return request<User>(`/users/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  })
}

/**
 * Live order updates (Server-Sent Events). Calls onOrder with the order's current state whenever an order is
 * created or its status changes, from this browser or anywhere else (curl, Postman, another tab).
 * The browser reconnects by itself if the connection drops; onStatus reports whether it's connected.
 * Returns a function that closes the stream.
 */
export function subscribeToOrders(
  onOrder: (order: Order) => void,
  onStatus: (connected: boolean) => void,
): () => void {
  const source = new EventSource(`${API}/orders/stream`)
  source.addEventListener('order', (e) => onOrder(JSON.parse((e as MessageEvent<string>).data) as Order))
  source.onopen = () => onStatus(true)
  source.onerror = () => onStatus(false) // EventSource retries automatically after an error
  return () => source.close()
}

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

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  let res: Response
  try {
    res = await fetch(url, init)
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
        ? 'The server rejected the order.'
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

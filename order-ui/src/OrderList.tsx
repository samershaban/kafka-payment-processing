import { useState } from 'react'
import type { Order } from './api'
import { StatusBadge } from './StatusBadge'

const money = new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD' })

// 0 means "All"
const PAGE_SIZES = [5, 10, 0]

export function OrderList({
  orders,
  loading,
  error,
  onRefresh,
}: {
  orders: Order[]
  loading: boolean
  error: string | null
  onRefresh: () => void
}) {
  const [pageSize, setPageSize] = useState(10)
  const [page, setPage] = useState(1)

  const sorted = [...orders].sort((a, b) => b.createdAt.localeCompare(a.createdAt))

  // Paging happens in the browser: GET /orders returns everything, we just slice it
  const pageCount = pageSize === 0 ? 1 : Math.max(1, Math.ceil(sorted.length / pageSize))
  const currentPage = Math.min(page, pageCount) // e.g. after switching to a bigger page size
  const start = pageSize === 0 ? 0 : (currentPage - 1) * pageSize
  const visible = pageSize === 0 ? sorted : sorted.slice(start, start + pageSize)

  return (
    <section className="card">
      <div className="card-header">
        <h2>Orders ({orders.length})</h2>
        <button className="secondary" onClick={onRefresh} disabled={loading}>
          {loading ? 'Loading…' : 'Refresh'}
        </button>
      </div>
      {error && <p className="error">{error}</p>}
      {!error && sorted.length === 0 && <p className="muted">No orders yet.</p>}
      {sorted.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Created</th>
                <th>Customer</th>
                <th>Item</th>
                <th className="num">Qty</th>
                <th className="num">Total</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {visible.map((o) => (
                <tr key={o.id}>
                  <td title={o.id}>{new Date(o.createdAt).toLocaleString()}</td>
                  <td>{o.customerId}</td>
                  <td>{o.item}</td>
                  <td className="num">{o.quantity}</td>
                  <td className="num">{money.format(o.totalPrice)}</td>
                  <td>
                    <StatusBadge status={o.status} />
                    {o.statusReason && <div className="muted small">{o.statusReason}</div>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {sorted.length > 0 && (
        <div className="pager">
          <label className="pager-size">
            <span className="muted">Show</span>
            <select
              value={pageSize}
              onChange={(e) => {
                setPageSize(Number(e.target.value))
                setPage(1)
              }}
            >
              {PAGE_SIZES.map((n) => (
                <option key={n} value={n}>
                  {n === 0 ? 'All' : n}
                </option>
              ))}
            </select>
          </label>
          <span className="muted small">
            {start + 1}–{start + visible.length} of {sorted.length}
          </span>
          {pageCount > 1 && (
            <div className="pager-nav">
              <button className="secondary" onClick={() => setPage(currentPage - 1)} disabled={currentPage === 1}>
                ‹ Prev
              </button>
              <span className="small">
                Page {currentPage} of {pageCount}
              </span>
              <button
                className="secondary"
                onClick={() => setPage(currentPage + 1)}
                disabled={currentPage === pageCount}
              >
                Next ›
              </button>
            </div>
          )}
        </div>
      )}
    </section>
  )
}

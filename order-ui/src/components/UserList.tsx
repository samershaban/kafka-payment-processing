import { useState } from 'react'
import { ApiError, type User } from '../api'
import { ConfirmDialog } from './ConfirmDialog'

type Filter = 'all' | 'enrolled' | 'not-enrolled'

/** Current time in the user's own timezone, e.g. "14:05" */
function localTime(timezone: string): string {
  try {
    return new Intl.DateTimeFormat(undefined, { timeZone: timezone, hour: '2-digit', minute: '2-digit' }).format(
      new Date(),
    )
  } catch {
    return ''
  }
}

export function UserList({
  users,
  loading,
  error,
  onRefresh,
  onSetEnrolled,
}: {
  users: User[]
  loading: boolean
  error: string | null
  onRefresh: () => void
  /** Saves the new enrolled value; rejects if the server refuses */
  onSetEnrolled: (user: User, enrolled: boolean) => Promise<User>
}) {
  const [filter, setFilter] = useState<Filter>('all')
  const [search, setSearch] = useState('')
  const [saving, setSaving] = useState<Set<string>>(new Set())
  const [rowErrors, setRowErrors] = useState<Record<string, string>>({})
  // The switch the user clicked, waiting for confirmation in the dialog
  const [pending, setPending] = useState<User | null>(null)

  const term = search.trim().toLowerCase()
  const visible = users.filter(
    (u) =>
      (filter === 'all' || (filter === 'enrolled' ? u.enrolled : !u.enrolled)) &&
      (term === '' ||
        [u.name, u.email, u.company, u.address, u.timezone, u.id].some((v) => v?.toLowerCase().includes(term))),
  )
  const enrolledCount = users.filter((u) => u.enrolled).length

  // Called after the user confirms in the dialog
  async function toggle(user: User) {
    setSaving((prev) => new Set(prev).add(user.id))
    setRowErrors(({ [user.id]: _, ...rest }) => rest)
    try {
      await onSetEnrolled(user, !user.enrolled)
    } catch (err) {
      setRowErrors((prev) => ({
        ...prev,
        [user.id]: err instanceof ApiError ? err.message : 'Could not save. Try again.',
      }))
    } finally {
      setSaving((prev) => {
        const next = new Set(prev)
        next.delete(user.id)
        return next
      })
    }
  }

  return (
    <section className="card">
      <div className="card-header">
        <h2>
          Users ({users.length}) <span className="muted small">· {enrolledCount} enrolled</span>
        </h2>
        <div className="actions">
          <label className="pager-size">
            <span className="muted">Show</span>
            <select value={filter} onChange={(e) => setFilter(e.target.value as Filter)}>
              <option value="all">All</option>
              <option value="enrolled">Enrolled</option>
              <option value="not-enrolled">Not enrolled</option>
            </select>
          </label>
          <button className="secondary" onClick={onRefresh} disabled={loading}>
            {loading ? 'Loading…' : 'Refresh'}
          </button>
        </div>
      </div>

      <div className="search">
        <input
          type="search"
          placeholder="Search name, email, company, address, timezone or ID"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          aria-label="Search users"
        />
        {(term || filter !== 'all') && (
          <span className="muted small" aria-live="polite">
            {visible.length} of {users.length} shown
          </span>
        )}
      </div>

      {error && <p className="error">{error}</p>}
      {!error && visible.length === 0 && (
        <p className="muted">{term ? `No users match “${search.trim()}”.` : 'No users match.'}</p>
      )}

      {visible.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Company</th>
                <th>Address</th>
                <th>Timezone</th>
                <th>Enrolled</th>
              </tr>
            </thead>
            <tbody>
              {visible.map((u) => (
                <tr key={u.id}>
                  <td>
                    <strong>{u.name}</strong>
                    <div className="small">
                      <a href={`mailto:${u.email}`}>{u.email}</a>
                    </div>
                    <div className="muted small mono">{u.id}</div>
                  </td>
                  <td>{u.company ?? <span className="muted">—</span>}</td>
                  <td>{u.address ?? <span className="muted">—</span>}</td>
                  <td>
                    {u.timezone}
                    <div className="muted small">{localTime(u.timezone)} local time</div>
                  </td>
                  <td>
                    <label className="switch">
                      <input
                        type="checkbox"
                        role="switch"
                        checked={u.enrolled}
                        // Don't save yet: ask first. The switch only moves once the save succeeds.
                        onChange={() => setPending(u)}
                        disabled={saving.has(u.id)}
                        aria-label={`Enrolled: ${u.name}`}
                      />
                      <span className="switch-track" aria-hidden="true" />
                      <span className="small">{saving.has(u.id) ? 'Saving…' : u.enrolled ? 'Yes' : 'No'}</span>
                    </label>
                    {rowErrors[u.id] && <div className="error small">{rowErrors[u.id]}</div>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <ConfirmDialog
        open={pending !== null}
        title={pending ? `${pending.enrolled ? 'Unenroll' : 'Enroll'} ${pending.name}?` : ''}
        confirmLabel={pending?.enrolled ? 'Unenroll' : 'Enroll'}
        onCancel={() => setPending(null)}
        onConfirm={() => {
          if (pending) toggle(pending)
          setPending(null)
        }}
      >
        {pending && (
          <>
            {pending.name} ({pending.email}) will be marked as{' '}
            <strong>{pending.enrolled ? 'not enrolled' : 'enrolled'}</strong>
          </>
        )}
      </ConfirmDialog>
    </section>
  )
}

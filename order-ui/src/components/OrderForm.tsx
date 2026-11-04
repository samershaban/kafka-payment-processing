import { useRef, useState, type ChangeEvent, type FormEvent, type InputHTMLAttributes } from 'react'
import { ApiError, createOrder, type CreateOrderRequest, type Order, type User } from '../api'
import { randomOrder } from '../utils/orderGenerator'

type Fields = { customerId: string; item: string; quantity: string; totalPrice: string }

const EMPTY: Fields = { customerId: '', item: '', quantity: '1', totalPrice: '' }

// Same rules as CreateOrderRequest.java (@NotBlank, @Min(1), @NotNull @DecimalMin("0.01"))
function validate(f: Fields): Record<string, string> {
  const errors: Record<string, string> = {}
  if (!f.customerId.trim()) errors.customerId = 'Select a customer'
  if (!f.item.trim()) errors.item = 'Required'
  const qty = Number(f.quantity)
  if (!Number.isInteger(qty) || qty < 1) errors.quantity = 'Must be a whole number, at least 1'
  const price = Number(f.totalPrice)
  if (f.totalPrice === '' || Number.isNaN(price) || price < 0.01) errors.totalPrice = 'Must be at least 0.01'
  return errors
}

export function OrderForm({
  users,
  usersError,
  onCreated,
}: {
  /** Existing users (loaded by App), sorted by name: the Customer options and the pool for random orders */
  users: User[]
  usersError: string | null
  onCreated: (order: Order) => void
}) {
  const [fields, setFields] = useState<Fields>(EMPTY)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  // Idempotency key of an order that was sent but not confirmed (e.g. network error or timeout).
  // Retrying the same order reuses the key, so the server can't create it twice.
  const pending = useRef<{ key: string; body: string } | null>(null)

  const set = (name: keyof Fields) => (e: ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setFields({ ...fields, [name]: e.target.value })


  // Fill every field with a generated order; you can still edit it before submitting
  function fillRandom(): Fields | null {
    if (users.length === 0) {
      setFormError(usersError ?? 'No users to pick from.')
      return null
    }
    const r = randomOrder(users)
    const filled: Fields = {
      customerId: r.customerId,
      item: r.item,
      quantity: String(r.quantity),
      totalPrice: r.totalPrice.toFixed(2),
    }
    setFields(filled)
    setErrors({})
    setFormError(null)
    return filled
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    submit(fields)
  }

  // Generate a random order, show it in the fields, and submit it right away
  function placeRandom() {
    const filled = fillRandom()
    if (filled) submit(filled)
  }

  // Takes the values explicitly: after fillRandom() the `fields` state isn't updated until the next render
  async function submit(values: Fields) {
    setFormError(null)

    const clientErrors = validate(values)
    setErrors(clientErrors)
    if (Object.keys(clientErrors).length > 0) return

    const req: CreateOrderRequest = {
      customerId: values.customerId.trim(),
      item: values.item.trim(),
      quantity: Number(values.quantity),
      totalPrice: Number(values.totalPrice),
    }

    // Reuse the key only when retrying the exact same order after a failure; anything else is a new order.
    const body = JSON.stringify(req)
    if (pending.current?.body !== body) {
      pending.current = { key: crypto.randomUUID(), body }
    }

    setSubmitting(true)
    try {
      const order = await createOrder(req, pending.current.key)
      // Success: the next click is a new order and gets a new key.
      pending.current = null
      // Keep the form filled in so the same order can be resubmitted or tweaked
      onCreated(order)
    } catch (err) {
      if (err instanceof ApiError) {
        setErrors(err.fieldErrors)
        setFormError(err.message)
      } else {
        setFormError('Something went wrong.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  const field = (name: keyof Fields, label: string, props: InputHTMLAttributes<HTMLInputElement> = {}) => (
    <label className="field">
      <span>{label}</span>
      <input
        name={name}
        value={fields[name]}
        onChange={set(name)}
        aria-invalid={!!errors[name]}
        disabled={submitting}
        {...props}
      />
      {errors[name] && <small className="error">{errors[name]}</small>}
    </label>
  )

  return (
    <form className="card" onSubmit={handleSubmit} noValidate>
      <div className="card-header">
        <h2>New order</h2>
        <button type="button" className="secondary" onClick={() => fillRandom()} disabled={submitting || users.length === 0}>
          Random order
        </button>
      </div>
      {usersError && <p className="muted small">{usersError}</p>}
      <div className="grid">
        <label className="field">
          <span>Customer</span>
          <select
            name="customerId"
            value={fields.customerId}
            onChange={set('customerId')}
            aria-invalid={!!errors.customerId}
            disabled={submitting || users.length === 0}
          >
            <option value="">{users.length === 0 ? 'No users available' : 'Select a user…'}</option>
            {users.map((u) => (
              <option key={u.id} value={u.id}>
                {u.name} ({u.email})
              </option>
            ))}
          </select>
          {errors.customerId && <small className="error">{errors.customerId}</small>}
          {/* The id that is sent as customerId. Always rendered (empty when nothing is selected) so it reserves its line.
              The line is cut with "…" when narrow; hovering (or focusing/tapping) shows the full id immediately. */}
          <span
            className="uuid-hint"
            tabIndex={fields.customerId ? 0 : undefined}
            // Inside a <label>, a click would move focus to the dropdown and hide the tooltip on touch screens
            onClick={(e) => e.preventDefault()}
          >
            <small className="selected-user muted mono" aria-live="polite">
              UUID:{fields.customerId}
            </small>
            {fields.customerId && (
              // Same text and classes as the line above, so it lines up exactly on top of it
              <small className="uuid-tooltip muted mono" role="tooltip">
                UUID:{fields.customerId}
              </small>
            )}
          </span>
        </label>
        {field('item', 'Item', { placeholder: 'Margherita pizza' })}
        {field('quantity', 'Quantity', { type: 'number', min: 1, step: 1 })}
        {field('totalPrice', 'Total price', { type: 'number', min: 0.01, step: 0.01, placeholder: '24.50' })}
      </div>
      {formError && <p className="error">{formError}</p>}
      <div className="actions">
        <button type="submit" disabled={submitting}>
          {submitting ? 'Placing…' : 'Place order'}
        </button>
        <button type="button" className="secondary" onClick={placeRandom} disabled={submitting || users.length === 0}>
          Place random order
        </button>
      </div>
    </form>
  )
}

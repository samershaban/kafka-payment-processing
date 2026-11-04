import { useEffect, useRef, type ReactNode } from 'react'

/**
 * A modal "are you sure?" dialog, built on the native <dialog> element: it traps focus, Esc cancels, and the
 * rest of the page can't be clicked while it's open. Rendered only while `open` is true.
 */
export function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  onConfirm,
  onCancel,
}: {
  open: boolean
  title: string
  children?: ReactNode
  confirmLabel?: string
  cancelLabel?: string
  onConfirm: () => void
  onCancel: () => void
}) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog
      ref={ref}
      className="confirm-dialog"
      aria-labelledby="confirm-dialog-title"
      // Esc key: the browser closes the dialog itself; treat that as Cancel
      onCancel={(e) => {
        e.preventDefault()
        onCancel()
      }}
      // Click on the dimmed backdrop (outside the dialog box) also cancels
      onClick={(e) => {
        if (e.target === ref.current) onCancel()
      }}
    >
      <div className="confirm-dialog-body">
        <h2 id="confirm-dialog-title">{title}</h2>
        {children && <div className="muted">{children}</div>}
        <div className="actions confirm-dialog-actions">
          <button type="button" className="secondary" onClick={onCancel}>
            {cancelLabel}
          </button>
          <button type="button" onClick={onConfirm} autoFocus>
            {confirmLabel}
          </button>
        </div>
      </div>
    </dialog>
  )
}

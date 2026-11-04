import { useCallback, useEffect, useState } from 'react'
import { ApiError, listUsers, updateUser, type User } from '../api'

/**
 * The list of users, shared by both pages: the Orders page uses it for the Customer dropdown and names, the
 * Users page shows and edits it. Loaded once in App, so a change on one page shows up on the other.
 */
export function useUsers() {
  const [users, setUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      const list = await listUsers()
      setUsers([...list].sort((a, b) => a.name.localeCompare(b.name)))
      setError(list.length === 0 ? 'No users yet. Add one with POST /users to place orders.' : null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not load users.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    reload()
  }, [reload])

  /** Saves the enrolled flag. PUT replaces the whole user, so the other fields are sent unchanged. */
  const setEnrolled = useCallback(async (user: User, enrolled: boolean): Promise<User> => {
    const { id, ...fields } = user
    const saved = await updateUser(id, { ...fields, enrolled })
    setUsers((prev) => prev.map((u) => (u.id === saved.id ? saved : u)))
    return saved
  }, [])

  return { users, loading, error, reload, setEnrolled }
}

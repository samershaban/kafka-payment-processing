import type { User } from '../api'
import { UserList } from '../components/UserList'

export function UsersPage({
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
  onSetEnrolled: (user: User, enrolled: boolean) => Promise<User>
}) {
  return (
    <>
      <div className="page-title">
        <h1>Users</h1>
      </div>
      <UserList users={users} loading={loading} error={error} onRefresh={onRefresh} onSetEnrolled={onSetEnrolled} />
    </>
  )
}

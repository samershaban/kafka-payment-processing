import { NavLink, Route, Routes } from 'react-router'
import { useUsers } from './hooks/useUsers'
import { OrdersPage } from './pages/OrdersPage'
import { UsersPage } from './pages/UsersPage'

/** Layout (navigation) and routes. Users are loaded here once and shared by both pages. */
export default function App() {
  const users = useUsers()

  return (
    <>
      <nav className="nav" aria-label="Main">
        <div className="nav-inner">
          <NavLink to="/" end>
            Orders
          </NavLink>
          <NavLink to="/users">Users</NavLink>
        </div>
      </nav>

      <main>
        <Routes>
          <Route path="/" element={<OrdersPage users={users.users} usersError={users.error} />} />
          <Route
            path="/users"
            element={
              <UsersPage
                users={users.users}
                loading={users.loading}
                error={users.error}
                onRefresh={users.reload}
                onSetEnrolled={users.setEnrolled}
              />
            }
          />
          <Route path="*" element={<p className="muted">Page not found.</p>} />
        </Routes>
      </main>
    </>
  )
}

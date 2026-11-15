import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './context/AuthContext'
import ProtectedRoute from './components/ProtectedRoute'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import ParentDashboard from './pages/ParentDashboard'
import ChildDashboard from './pages/ChildDashboard'
import { homePathFor } from './utils/routes'

function HomeRedirect() {
  const { user, loading } = useAuth()
  if (loading) return <p className="p-8 text-stone-600">Loading…</p>
  return <Navigate to={user ? homePathFor(user.role) : '/login'} replace />
}

/** Login/register pages bounce already-logged-in users to their dashboard. */
function PublicOnly({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth()
  if (loading) return <p className="p-8 text-stone-600">Loading…</p>
  if (user) return <Navigate to={homePathFor(user.role)} replace />
  return <>{children}</>
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomeRedirect />} />
      <Route path="/login" element={<PublicOnly><LoginPage /></PublicOnly>} />
      <Route path="/register" element={<PublicOnly><RegisterPage /></PublicOnly>} />

      <Route element={<ProtectedRoute role="PARENT" />}>
        <Route path="/parent" element={<ParentDashboard />} />
      </Route>
      <Route element={<ProtectedRoute role="CHILD" />}>
        <Route path="/child" element={<ChildDashboard />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import Navbar from './Navbar'
import type { Role } from '../types'
import { homePathFor } from '../utils/routes'

/** Only lets in logged-in users with the right role. (The backend enforces this too; this is just UX.) */
export default function ProtectedRoute({ role }: { role: Role }) {
  const { user, loading } = useAuth()

  if (loading) return <p className="p-8 text-stone-600">Loading…</p>
  if (!user) return <Navigate to="/login" replace />
  if (user.role !== role) return <Navigate to={homePathFor(user.role)} replace />

  return (
    <>
      <Navbar />
      <main className="mx-auto max-w-4xl space-y-6 px-6 py-8">
        <Outlet />
      </main>
    </>
  )
}

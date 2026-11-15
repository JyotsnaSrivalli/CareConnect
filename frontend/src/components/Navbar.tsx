import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function Navbar() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <header className="border-b border-stone-200 bg-white">
      <div className="mx-auto flex max-w-4xl items-center justify-between px-6 py-3">
        <span className="text-lg font-semibold text-teal-800">CareConnect</span>
        {user && (
          <div className="flex items-center gap-3 text-sm">
            <span className="text-stone-700">{user.name}</span>
            <span className="rounded-full bg-teal-100 px-2 py-0.5 text-xs font-medium text-teal-800">
              {user.role === 'PARENT' ? 'Parent' : 'Caregiver'}
            </span>
            <button onClick={handleLogout} className="rounded-lg border border-stone-300 px-3 py-1 hover:bg-stone-100">
              Log out
            </button>
          </div>
        )}
      </div>
    </header>
  )
}

import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { getErrorMessage } from '../api/errors'
import { homePathFor } from '../utils/routes'
import Alert from '../components/Alert'
import PasswordInput from '../components/PasswordInput'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      const user = await login(email, password)
      navigate(homePathFor(user.role), { replace: true })
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-stone-50 p-6">
      <div className="w-full max-w-md rounded-2xl border border-stone-200 bg-white p-8">
        <h1 className="text-2xl font-semibold text-teal-800">CareConnect</h1>
        <p className="mt-1 text-stone-600">Log in to your account</p>

        <form onSubmit={submit} className="mt-6 space-y-4">
          <label className="block text-sm font-medium text-stone-700">
            Email
            <input type="email" required autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)}
              className="mt-1 w-full rounded-lg border border-stone-300 px-3 py-2" />
          </label>
          <PasswordInput label="Password" autoComplete="current-password" value={password} onChange={setPassword} />
          {error && <Alert>{error}</Alert>}
          <button disabled={busy} className="w-full rounded-lg bg-teal-700 py-2.5 font-medium text-white hover:bg-teal-800 disabled:opacity-60">
            {busy ? 'Logging in…' : 'Log in'}
          </button>
        </form>

        <p className="mt-6 text-sm text-stone-600">
          New here? <Link to="/register" className="text-teal-800 underline">Create an account</Link>
        </p>
      </div>
    </main>
  )
}

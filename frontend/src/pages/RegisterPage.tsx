import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { getErrorMessage } from '../api/errors'
import { homePathFor } from '../utils/routes'
import Alert from '../components/Alert'
import PasswordInput from '../components/PasswordInput'
import type { Role } from '../types'

const ROLES: { value: Role; title: string; hint: string }[] = [
  { value: 'PARENT', title: 'I am a parent', hint: 'Track my own medicines' },
  { value: 'CHILD', title: 'I am a child / caregiver', hint: 'Look after a parent from afar' },
]

export default function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<Role>('PARENT')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      const user = await register({ name, email, password, role })
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
        <h1 className="text-2xl font-semibold text-teal-800">Create your account</h1>

        <form onSubmit={submit} className="mt-6 space-y-4">
          <fieldset>
            <legend className="mb-2 text-sm font-medium text-stone-700">Who are you?</legend>
            <div className="grid grid-cols-2 gap-3">
              {ROLES.map((r) => (
                <label key={r.value}
                  className={`cursor-pointer rounded-xl border p-3 text-sm ${role === r.value ? 'border-teal-700 bg-teal-50' : 'border-stone-300'}`}>
                  <input type="radio" name="role" className="sr-only" checked={role === r.value} onChange={() => setRole(r.value)} />
                  <span className="block font-medium text-stone-900">{r.title}</span>
                  <span className="block text-stone-600">{r.hint}</span>
                </label>
              ))}
            </div>
          </fieldset>

          <label className="block text-sm font-medium text-stone-700">
            Full name
            <input required maxLength={100} value={name} onChange={(e) => setName(e.target.value)}
              className="mt-1 w-full rounded-lg border border-stone-300 px-3 py-2" />
          </label>
          <label className="block text-sm font-medium text-stone-700">
            Email
            <input type="email" required autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)}
              className="mt-1 w-full rounded-lg border border-stone-300 px-3 py-2" />
          </label>
          <PasswordInput label={<>Password <span className="font-normal text-stone-500">(at least 8 characters)</span></>}
            autoComplete="new-password" minLength={8} maxLength={72} value={password} onChange={setPassword} />

          {error && <Alert>{error}</Alert>}
          <button disabled={busy} className="w-full rounded-lg bg-teal-700 py-2.5 font-medium text-white hover:bg-teal-800 disabled:opacity-60">
            {busy ? 'Creating account…' : 'Create account'}
          </button>
        </form>

        <p className="mt-6 text-sm text-stone-600">
          Already registered? <Link to="/login" className="text-teal-800 underline">Log in</Link>
        </p>
      </div>
    </main>
  )
}

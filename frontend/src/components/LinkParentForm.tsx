import { useState } from 'react'
import type { FormEvent } from 'react'
import { connectWithCode } from '../api/family'
import { getErrorMessage } from '../api/errors'
import Alert from './Alert'

export default function LinkParentForm({ onLinked }: { onLinked: () => void }) {
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    setSuccess('')
    try {
      const connection = await connectWithCode(code.trim())
      setSuccess(`Request sent. ${connection.parent.name} needs to approve it before you can see anything.`)
      setCode('')
      onLinked()
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="rounded-2xl border border-stone-200 bg-white p-6">
      <h2 className="text-lg font-semibold text-stone-900">Connect to a parent</h2>
      <p className="mt-1 text-sm text-stone-600">Enter the 8-character code your parent shared with you.</p>

      <form onSubmit={submit} className="mt-4 flex flex-wrap gap-3">
        <input
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          maxLength={12}
          required
          placeholder="ABCD2345"
          aria-label="Invitation code"
          className="w-48 rounded-lg border border-stone-300 px-3 py-2 font-mono text-lg uppercase tracking-widest"
        />
        <button
          disabled={busy}
          className="rounded-lg bg-teal-700 px-4 py-2 font-medium text-white hover:bg-teal-800 disabled:opacity-60"
        >
          {busy ? 'Sending…' : 'Send request'}
        </button>
      </form>

      <div className="mt-3 space-y-3">
        {error && <Alert>{error}</Alert>}
        {success && <Alert kind="success">{success}</Alert>}
      </div>
    </section>
  )
}

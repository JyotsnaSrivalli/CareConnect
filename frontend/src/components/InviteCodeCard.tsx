import { useState } from 'react'
import { createInvite } from '../api/family'
import { getErrorMessage } from '../api/errors'
import type { Invite } from '../types'
import Alert from './Alert'

export default function InviteCodeCard() {
  const [invite, setInvite] = useState<Invite | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [copied, setCopied] = useState(false)

  const generate = async () => {
    setBusy(true)
    setError('')
    setCopied(false)
    try {
      setInvite(await createInvite())
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const copy = async () => {
    if (!invite) return
    await navigator.clipboard.writeText(invite.code)
    setCopied(true)
  }

  return (
    <section className="rounded-2xl border border-stone-200 bg-white p-6">
      <h2 className="text-lg font-semibold text-stone-900">Invite a family member</h2>
      <p className="mt-1 text-sm text-stone-600">
        Create a code and share it with your son or daughter. They enter it in their CareConnect account, then you
        approve the request below. Each code works once and expires in 24 hours.
      </p>

      {error && <div className="mt-4"><Alert>{error}</Alert></div>}

      {invite && (
        <div className="mt-4 flex flex-wrap items-center gap-4 rounded-xl bg-teal-50 p-4">
          <span className="font-mono text-3xl font-semibold tracking-widest text-teal-900">{invite.code}</span>
          <button onClick={copy} className="rounded-lg border border-teal-300 px-3 py-1.5 text-sm text-teal-900 hover:bg-teal-100">
            {copied ? 'Copied' : 'Copy code'}
          </button>
          <span className="text-xs text-stone-600">Expires {new Date(invite.expiresAt).toLocaleString()}</span>
        </div>
      )}

      <button
        onClick={generate}
        disabled={busy}
        className="mt-4 rounded-lg bg-teal-700 px-4 py-2 font-medium text-white hover:bg-teal-800 disabled:opacity-60"
      >
        {busy ? 'Creating…' : invite ? 'Create a new code' : 'Create invitation code'}
      </button>
    </section>
  )
}

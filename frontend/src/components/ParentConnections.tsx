import { useState } from 'react'
import { decide } from '../api/family'
import { getErrorMessage } from '../api/errors'
import { useConnections } from '../hooks/useConnections'
import Alert from './Alert'
import StatusBadge from './StatusBadge'

export default function ParentConnections() {
  const { connections, loading, error, reload } = useConnections()
  const [actionError, setActionError] = useState('')
  const [shareChoice, setShareChoice] = useState<Record<number, boolean>>({})
  const [busyId, setBusyId] = useState<number | null>(null)

  const act = async (id: number, approve: boolean) => {
    setBusyId(id)
    setActionError('')
    try {
      await decide(id, approve, shareChoice[id] ?? false)
      await reload()
    } catch (e) {
      setActionError(getErrorMessage(e))
    } finally {
      setBusyId(null)
    }
  }

  return (
    <section className="rounded-2xl border border-stone-200 bg-white p-6">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-stone-900">Family connections</h2>
        <button onClick={() => void reload()} className="text-sm text-teal-800 underline">Refresh</button>
      </div>

      {(error || actionError) && <div className="mt-3"><Alert>{error || actionError}</Alert></div>}
      {loading && <p className="mt-3 text-sm text-stone-600">Loading…</p>}
      {!loading && connections.length === 0 && (
        <p className="mt-3 text-sm text-stone-600">No one has asked to connect yet. Share an invitation code to get started.</p>
      )}

      <ul className="mt-4 divide-y divide-stone-100">
        {connections.map((c) => (
          <li key={c.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
            <div>
              <p className="font-medium text-stone-900">{c.child.name}</p>
              <p className="text-sm text-stone-600">{c.child.email}</p>
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <StatusBadge status={c.status} />
              {c.status === 'PENDING' && (
                <>
                  <label className="flex items-center gap-1.5 text-sm text-stone-700">
                    <input
                      type="checkbox"
                      checked={shareChoice[c.id] ?? false}
                      onChange={(e) => setShareChoice({ ...shareChoice, [c.id]: e.target.checked })}
                    />
                    Share my daily check-ins
                  </label>
                  <button
                    disabled={busyId === c.id}
                    onClick={() => act(c.id, true)}
                    className="rounded-lg bg-teal-700 px-3 py-1.5 text-sm font-medium text-white hover:bg-teal-800 disabled:opacity-60"
                  >
                    Approve
                  </button>
                  <button
                    disabled={busyId === c.id}
                    onClick={() => act(c.id, false)}
                    className="rounded-lg border border-stone-300 px-3 py-1.5 text-sm hover:bg-stone-100 disabled:opacity-60"
                  >
                    Reject
                  </button>
                </>
              )}
              {c.status === 'APPROVED' && (
                <button
                  disabled={busyId === c.id}
                  onClick={() => act(c.id, false)}
                  className="rounded-lg border border-stone-300 px-3 py-1.5 text-sm hover:bg-stone-100 disabled:opacity-60"
                >
                  Remove access
                </button>
              )}
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}

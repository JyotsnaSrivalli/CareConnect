import { useAuth } from '../context/AuthContext'
import { useConnections } from '../hooks/useConnections'
import LinkParentForm from '../components/LinkParentForm'
import StatusBadge from '../components/StatusBadge'
import Alert from '../components/Alert'
import ChildMedications from '../components/ChildMedications'
import AdherenceSummary from '../components/AdherenceSummary'
import BlisterStrips from '../components/BlisterStrips'
import CheckinHistory from '../components/CheckinHistory'
import DoseTracker from '../components/DoseTracker'

export default function ChildDashboard() {
  const { user } = useAuth()
  const { connections, loading, error, reload } = useConnections()

  return (
    <>
      <h1 className="text-2xl font-semibold text-stone-900">Welcome, {user?.name}</h1>

      <LinkParentForm onLinked={() => void reload()} />

      <section className="rounded-2xl border border-stone-200 bg-white p-6">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-stone-900">Your parents</h2>
          <button onClick={() => void reload()} className="text-sm text-teal-800 underline">Refresh</button>
        </div>

        {error && <div className="mt-3"><Alert>{error}</Alert></div>}
        {loading && <p className="mt-3 text-sm text-stone-600">Loading…</p>}
        {!loading && connections.length === 0 && (
          <p className="mt-3 text-sm text-stone-600">You are not connected to anyone yet. Enter a code above.</p>
        )}

        <ul className="mt-4 divide-y divide-stone-100">
          {connections.map((c) => (
            <li key={c.id} className="py-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="font-medium text-stone-900">{c.parent.name}</p>
                <StatusBadge status={c.status} />
              </div>
              {c.status === 'PENDING' && (
                <p className="mt-1 text-sm text-stone-600">Ask {c.parent.name} to approve your request.</p>
              )}
              {c.status === 'APPROVED' && (
                <div className="mt-4 space-y-4">
                  <AdherenceSummary parentId={c.parent.id} parentName={c.parent.name} />
                  <BlisterStrips editable={false} parentId={c.parent.id} parentName={c.parent.name} />
                  <DoseTracker editable={false} parentId={c.parent.id} parentName={c.parent.name} />
                  <CheckinHistory parentId={c.parent.id} parentName={c.parent.name} sharingDisabled={!c.shareCheckins} />
                  <ChildMedications parentId={c.parent.id} parentName={c.parent.name} />
                </div>
              )}
              {c.status === 'REJECTED' && (
                <p className="mt-1 text-sm text-stone-600">Access was declined or removed. Ask for a new code to try again.</p>
              )}
            </li>
          ))}
        </ul>
      </section>
    </>
  )
}

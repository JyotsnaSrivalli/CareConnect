import { useEffect } from 'react'
import { useCheckinHistory } from '../hooks/useCheckins'
import { ENERGY_LABEL, FEELING_LABEL, formatDateLabel } from '../utils/format'
import Alert from './Alert'

interface Props {
  parentId?: number                 // child only
  parentName?: string               // child only, for the heading and messages
  sharingDisabled?: boolean         // child only: the parent did not turn on "share check-ins"
  refreshKey?: number               // changes after a save -> reload
  selectedDate?: string             // parent only: highlights the day shown in the form
  onEdit?: (date: string) => void   // parent only: gives every entry an Edit button. A child gets none.
}

export default function CheckinHistory({ parentId, parentName, sharingDisabled = false, refreshKey = 0, selectedDate, onEdit }: Props) {
  const { checkins, loading, error, forbidden, reload } = useCheckinHistory(parentId, !sharingDisabled)

  useEffect(() => {
    if (refreshKey > 0) void reload()
  }, [refreshKey, reload])

  const heading = parentName ? `${parentName}’s check-ins` : 'Previous check-ins'
  const notShared = sharingDisabled || forbidden

  return (
    <section className="space-y-4 rounded-2xl border border-stone-200 bg-white p-6" aria-label="Check-in history">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold text-stone-900">{heading}</h2>
        {!notShared && <button onClick={() => void reload()} className="text-sm text-teal-800 underline">Refresh</button>}
      </div>

      {notShared && (
        <p className="rounded-lg border border-stone-200 bg-stone-50 px-4 py-3 text-sm text-stone-700">
          {parentName ?? 'This parent'} has not turned on check-in sharing for you, so their check-ins are not visible.
          Only {parentName ?? 'the parent'} can change this.
        </p>
      )}
      {!notShared && error && <Alert>{error}</Alert>}
      {!notShared && loading && <p className="text-sm text-stone-600">Loading…</p>}
      {!notShared && !loading && !error && checkins.length === 0 && (
        <p className="text-sm text-stone-600">
          {onEdit ? 'No check-ins yet. Fill in the form above to add your first one.' : 'No check-ins have been recorded yet.'}
        </p>
      )}

      {!notShared && checkins.length > 0 && (
        <ul className="space-y-2">
          {checkins.map((c) => (
            <li key={c.id}
              className={`rounded-xl border p-3 ${selectedDate === c.date ? 'border-teal-300 bg-teal-50' : 'border-stone-200'}`}>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="font-medium text-stone-900">{formatDateLabel(c.date)}</p>
                <div className="flex items-center gap-2 text-xs">
                  <span className="rounded-full bg-teal-100 px-2.5 py-0.5 font-medium text-teal-900">
                    Feeling: {FEELING_LABEL[c.feeling]}
                  </span>
                  <span className="rounded-full bg-stone-100 px-2.5 py-0.5 font-medium text-stone-800">
                    Energy: {c.energyLevel ? ENERGY_LABEL[c.energyLevel] : 'Not recorded'}
                  </span>
                  {onEdit && (
                    <button onClick={() => onEdit(c.date)} aria-label={`Edit check-in for ${formatDateLabel(c.date)}`}
                      className="text-sm text-teal-800 underline">
                      Edit
                    </button>
                  )}
                </div>
              </div>
              {c.symptoms && <p className="mt-2 text-sm text-stone-700"><b>Symptoms or concerns:</b> {c.symptoms}</p>}
              {c.notes && <p className="mt-1 text-sm text-stone-700"><b>Notes:</b> {c.notes}</p>}
            </li>
          ))}
        </ul>
      )}
      {!notShared && checkins.length >= 30 && (
        <p className="text-xs text-stone-500">Showing the 30 most recent check-ins.</p>
      )}
    </section>
  )
}

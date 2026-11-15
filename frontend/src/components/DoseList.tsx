import { formatAmount, formatClock, formatTime } from '../utils/format'
import type { Dose } from '../types'

interface Props {
  doses: Dose[]
  editable: boolean                       // false on the child dashboard: no buttons at all
  busyScheduleId: number | null
  onMark: (dose: Dose) => void
  onUndo: (dose: Dose) => void
}

export default function DoseList({ doses, editable, busyScheduleId, onMark, onUndo }: Props) {
  // group the doses by time, keeping the order the server sent (already sorted by time)
  const groups: { time: string; doses: Dose[] }[] = []
  for (const dose of doses) {
    const last = groups[groups.length - 1]
    if (last && last.time === dose.scheduledTime) last.doses.push(dose)
    else groups.push({ time: dose.scheduledTime, doses: [dose] })
  }

  return (
    <div className="space-y-4">
      {groups.map((group) => (
        <section key={group.time}>
          <h4 className="text-sm font-semibold text-stone-700">{formatTime(group.time)}</h4>
          <ul className="mt-2 space-y-2">
            {group.doses.map((d) => {
              const taken = d.status === 'TAKEN'
              const busy = busyScheduleId === d.scheduleId
              return (
                <li key={d.scheduleId}
                  className={`flex flex-wrap items-center justify-between gap-3 rounded-xl border p-3 ${taken ? 'border-teal-200 bg-teal-50' : 'border-stone-200 bg-white'}`}>
                  <div>
                    <p className="font-medium text-stone-900">
                      {d.medicationName}{d.strength && <span className="font-normal text-stone-600"> – {d.strength}</span>}
                    </p>
                    <p className="text-sm text-stone-600">
                      {formatAmount(d.dosagePerDose, d.quantityUnit)}
                      {d.remainingQuantity !== null && <> · {formatAmount(d.remainingQuantity, d.quantityUnit)} left</>}
                      {d.lowStock && <span className="ml-2 rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-800">Low stock</span>}
                    </p>
                  </div>

                  <div className="flex items-center gap-3">
                    {taken ? (
                      <>
                        <span className="rounded-full bg-teal-100 px-2.5 py-0.5 text-xs font-medium text-teal-900">
                          Taken{d.takenAt && ` at ${formatClock(d.takenAt)}`}
                        </span>
                        {editable && d.logId !== null && (
                          <button disabled={busy} onClick={() => onUndo(d)}
                            className="text-sm text-stone-600 underline hover:text-red-700 disabled:opacity-60">
                            Undo
                          </button>
                        )}
                      </>
                    ) : (
                      <>
                        <span className="rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-medium text-amber-900">Not confirmed yet</span>
                        {editable && (
                          <button disabled={busy} onClick={() => onMark(d)}
                            className="rounded-lg bg-teal-700 px-3 py-1.5 text-sm font-medium text-white hover:bg-teal-800 disabled:opacity-60">
                            {busy ? 'Saving…' : 'Mark as taken'}
                          </button>
                        )}
                      </>
                    )}
                  </div>
                </li>
              )
            })}
          </ul>
        </section>
      ))}
    </div>
  )
}

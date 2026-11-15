import { useEffect, useState } from 'react'
import { useAdherence } from '../hooks/useAdherence'
import { formatAmount, formatDateLabel, todayLocal } from '../utils/format'
import type { AdherenceMedication, AdherenceSummary as Summary } from '../types'
import Alert from './Alert'

interface Props {
  parentId?: number        // child only
  parentName?: string      // child only, for the heading
  version?: number         // changes when a tablet / dose was confirmed or undone elsewhere -> reload
}

/** "1 of 2 tablets" style text. */
function ofText(confirmed: number, expected: number | null, unit: string) {
  return expected === null ? `${confirmed} ${unit} confirmed` : `${confirmed} of ${expected} ${unit} confirmed`
}

function headingText(parentName: string | undefined, isToday: boolean, date: string) {
  const when = isToday ? 'today' : `on ${formatDateLabel(date)}`
  if (parentName) return `${parentName}’s medication adherence ${when}`
  return isToday ? 'Today’s medication adherence' : `Medication adherence ${when}`
}

function ProgressBar({ percent }: { percent: number }) {
  return (
    <div className="mt-2 h-3 overflow-hidden rounded-full bg-stone-200"
      role="progressbar" aria-label="Completion" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent}>
      <div className="h-full bg-teal-600 transition-all" style={{ width: `${percent}%` }} />
    </div>
  )
}

function MedicationLine({ m, expectedAvailable }: { m: AdherenceMedication; expectedAvailable: boolean }) {
  return (
    <li className="rounded-xl border border-stone-200 p-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <p className="font-medium text-stone-900">
          {m.name}{m.strength && <span className="font-normal text-stone-600"> – {m.strength}</span>}
        </p>
        {expectedAvailable && m.completionPercent !== null && (
          <span className="text-sm font-medium text-teal-800">{m.completionPercent}%</span>
        )}
      </div>

      <p className="mt-1 text-sm text-stone-800">
        {ofText(m.confirmed, expectedAvailable ? m.expected : null, m.unit)}
        {expectedAvailable && m.remaining !== null && m.remaining > 0 && (
          <span className="text-stone-500"> · {m.remaining} still to confirm</span>
        )}
      </p>
      {expectedAvailable && m.extraConfirmations !== null && m.extraConfirmations > 0 && (
        <p className="mt-1 text-sm text-amber-800">
          {m.extraConfirmations} more confirmed than expected for this medicine.
        </p>
      )}

      {/* Physical stock is a different thing from confirmed doses, so it gets its own line and wording.
          It is always the stock NOW (the database does not keep stock history), so a past date says "right now". */}
      {m.physicalStock && (
        <p className="mt-2 border-t border-dashed border-stone-200 pt-2 text-xs text-stone-600">
          Stock in the box{expectedAvailable ? '' : ' right now'}: <b>{formatAmount(m.physicalStock.remaining, m.physicalStock.unit)}</b> left
          {m.physicalStock.lowStock && (
            <span className="ml-2 rounded-full bg-red-100 px-2 py-0.5 font-medium text-red-800">Low stock</span>
          )}
        </p>
      )}
    </li>
  )
}

function TodayTotals({ s }: { s: Summary }) {
  if (s.expectedTotal === 0 || s.completionPercent === null) {
    return (
      <p className="text-sm text-stone-700">
        Nothing is scheduled for this day yet.{s.confirmedTotal > 0 && ` ${s.confirmedTotal} confirmed.`}
      </p>
    )
  }
  return (
    <div>
      <p className="text-sm text-stone-800">
        <b>{s.confirmedTotal}</b> confirmed · <b>{s.expectedTotal}</b> expected · {s.completionPercent}% completed
        {s.remainingTotal !== null && s.remainingTotal > 0 && (
          <span className="text-stone-500"> · {s.remainingTotal} still to confirm</span>
        )}
      </p>
      <ProgressBar percent={s.completionPercent} />
      {s.extraConfirmations !== null && s.extraConfirmations > 0 && (
        <p className="mt-2 text-sm text-amber-800">
          {s.extraConfirmations} confirmation{s.extraConfirmations === 1 ? '' : 's'} more than expected today.
        </p>
      )}
    </div>
  )
}

export default function AdherenceSummary({ parentId, parentName, version = 0 }: Props) {
  const today = todayLocal()
  const [date, setDate] = useState(today)
  const { summary, loading, error, reload } = useAdherence(date, parentId)

  useEffect(() => {
    if (version > 0) void reload()
  }, [version, reload])

  const isToday = date === today

  return (
    <section className="space-y-4 rounded-2xl border border-stone-200 bg-white p-6" aria-label="Medication adherence summary">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-xl font-semibold text-stone-900">
          {headingText(parentName, isToday, date)}
        </h2>
        <div className="flex items-center gap-2 text-sm">
          <label className="text-stone-600">
            Day{' '}
            <input type="date" value={date} max={today} required
              onChange={(e) => e.target.value && setDate(e.target.value)}
              className="rounded-lg border border-stone-300 px-2 py-1" />
          </label>
          {!isToday && <button onClick={() => setDate(today)} className="text-teal-800 underline">Back to today</button>}
          <button onClick={() => void reload()} className="text-teal-800 underline">Refresh</button>
        </div>
      </div>

      {error && <Alert>{error}</Alert>}
      {loading && !summary && <p className="text-sm text-stone-600">Loading…</p>}

      {summary && !error && (
        <div className={loading ? 'space-y-4 opacity-60' : 'space-y-4'}>
          {summary.expectedAvailable ? (
            <TodayTotals s={summary} />
          ) : (
            <>
              <p className="rounded-lg border border-stone-200 bg-stone-50 px-4 py-3 text-sm text-stone-700">
                {summary.message}
              </p>
              <p className="text-sm text-stone-800"><b>{summary.confirmedTotal}</b> confirmed on this day</p>
            </>
          )}

          {summary.medications.length === 0 ? (
            <p className="text-sm text-stone-600">
              {summary.expectedAvailable
                ? 'No medicines are due today. Add a medicine with dose times to see it here.'
                : 'Nothing was confirmed on this day.'}
            </p>
          ) : (
            <ul className="space-y-2">
              {summary.medications.map((m) => (
                <MedicationLine key={m.medicationId} m={m} expectedAvailable={summary.expectedAvailable} />
              ))}
            </ul>
          )}

          <p className="text-xs text-stone-500">
            Totals count confirmations: each tablet, capsule or syrup dose that is confirmed counts once.
            Nothing is marked taken or missed automatically, and looking at this page never changes your stock.
          </p>
        </div>
      )}
    </section>
  )
}

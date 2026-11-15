import { useEffect, useRef, useState } from 'react'
import { markTaken, undoDose } from '../api/logs'
import { getErrorMessage } from '../api/errors'
import { useDoses } from '../hooks/useDoses'
import { todayLocal } from '../utils/format'
import type { Dose } from '../types'
import Alert from './Alert'
import DoseList from './DoseList'
import DoseProgress from './DoseProgress'

interface Props {
  editable: boolean            // true = parent (buttons), false = child (read-only)
  parentId?: number            // child only
  parentName?: string          // child only, for the heading
  version?: number             // changes when medicines/stock were edited elsewhere -> reload
  onChanged?: () => void       // tell the page that stock changed
}

export default function DoseTracker({ editable, parentId, parentName, version = 0, onChanged }: Props) {
  const today = todayLocal()
  const [date, setDate] = useState(today)
  const { doses, summary, loading, error, reload } = useDoses(date, parentId)
  const [actionError, setActionError] = useState('')
  const [busyScheduleId, setBusyScheduleId] = useState<number | null>(null)
  const inFlight = useRef(false)   // set immediately, so a fast double click can never start two requests

  useEffect(() => {
    if (version > 0) void reload()
  }, [version, reload])

  // The UI only changes after the server confirms: we call the API, then re-read the data from the server.
  const run = async (dose: Dose, action: () => Promise<unknown>) => {
    if (inFlight.current) return
    inFlight.current = true
    setBusyScheduleId(dose.scheduleId)
    setActionError('')
    try {
      await action()
      await reload()
      onChanged?.()
    } catch (e) {
      setActionError(getErrorMessage(e))
      await reload()               // show the real state (e.g. someone else already confirmed it)
    } finally {
      inFlight.current = false
      setBusyScheduleId(null)
    }
  }

  const onMark = (dose: Dose) => void run(dose, () => markTaken(dose.scheduleId, date))
  const onUndo = (dose: Dose) => {
    if (dose.logId === null) return
    if (!window.confirm(`Undo ${dose.medicationName}? It will go back to “not confirmed” and the amount is added back to your stock.`)) return
    const logId = dose.logId
    void run(dose, () => undoDose(logId))
  }

  const isToday = date === today

  // This tracker is only for syrup (ml). Tablets and capsules live on the blister strips above it.
  if (!loading && !error && !actionError && isToday && doses.length === 0) return null

  return (
    <section className="space-y-4 rounded-2xl border border-stone-200 bg-white p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-xl font-semibold text-stone-900">
          {parentName ? `${parentName}’s ` : ''}{isToday ? 'Today’s syrup doses' : `Syrup doses on ${date}`}
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

      {summary && <DoseProgress summary={summary} />}
      {(error || actionError) && <Alert>{error || actionError}</Alert>}
      {loading && <p className="text-sm text-stone-600">Loading…</p>}
      {!loading && !error && doses.length === 0 && (
        <p className="text-sm text-stone-600">
          {isToday ? 'No doses are scheduled for today. Add dose times to a medicine to see them here.' : 'No doses recorded for this day.'}
        </p>
      )}

      <DoseList doses={doses} editable={editable} busyScheduleId={busyScheduleId} onMark={onMark} onUndo={onUndo} />
    </section>
  )
}

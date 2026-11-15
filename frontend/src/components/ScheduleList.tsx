import { useState } from 'react'
import type { FormEvent } from 'react'
import { addSchedule, removeSchedule } from '../api/medications'
import { getErrorMessage } from '../api/errors'
import { formatTime } from '../utils/format'
import type { Medication } from '../types'
import Alert from './Alert'

interface Props {
  medication: Medication
  editable: boolean
  onChanged: () => void
}

export default function ScheduleList({ medication, editable, onChanged }: Props) {
  const [time, setTime] = useState('08:00')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const add = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await addSchedule(medication.id, time)
      onChanged()
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const remove = async (scheduleId: number) => {
    setError('')
    try {
      await removeSchedule(medication.id, scheduleId)
      onChanged()
    } catch (err) {
      setError(getErrorMessage(err))
    }
  }

  return (
    <div>
      <h4 className="text-sm font-medium text-stone-700">Dose times</h4>
      {medication.schedules.length === 0 && <p className="mt-1 text-sm text-stone-500">No times set yet.</p>}
      <ul className="mt-2 flex flex-wrap gap-2">
        {medication.schedules.map((s) => (
          <li key={s.id} className="flex items-center gap-1 rounded-full bg-stone-100 px-3 py-1 text-sm text-stone-800">
            {formatTime(s.doseTime)}
            {editable && (
              <button onClick={() => remove(s.id)} aria-label={`Remove ${formatTime(s.doseTime)}`}
                className="ml-1 text-stone-500 hover:text-red-700">
                ×
              </button>
            )}
          </li>
        ))}
      </ul>

      {editable && (
        <form onSubmit={add} className="mt-3 flex items-center gap-2">
          <input type="time" required value={time} onChange={(e) => setTime(e.target.value)}
            aria-label="New dose time" className="rounded-lg border border-stone-300 px-2 py-1 text-sm" />
          <button disabled={busy} className="rounded-lg border border-teal-700 px-3 py-1 text-sm text-teal-800 hover:bg-teal-50 disabled:opacity-60">
            Add time
          </button>
        </form>
      )}
      {error && <div className="mt-2"><Alert>{error}</Alert></div>}
    </div>
  )
}

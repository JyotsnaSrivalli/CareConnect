import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { getCheckinForDate, saveCheckin } from '../api/checkins'
import { getErrorMessage } from '../api/errors'
import { ENERGY_LABEL, FEELING_LABEL, formatDateLabel, todayLocal } from '../utils/format'
import type { Checkin, EnergyLevel, NewFeeling } from '../types'
import Alert from './Alert'

const FEELINGS: NewFeeling[] = ['GOOD', 'OKAY', 'NOT_GOOD']
const ENERGIES: EnergyLevel[] = ['HIGH', 'MODERATE', 'LOW']

interface Props {
  date: string                          // the day being filled in (kept by the parent component)
  onDateChange: (date: string) => void
  onSaved: () => void                   // tell the page to refresh the history
}

/** A row of big radio "pills". The real <input type="radio"> stays in the page (screen readers and keyboard work). */
function Choice<T extends string>({ legend, name, options, labels, value, onChange }: {
  legend: string; name: string; options: T[]; labels: Record<string, string>; value: T | ''; onChange: (v: T) => void
}) {
  return (
    <fieldset>
      <legend className="text-sm font-medium text-stone-700">{legend}</legend>
      <div className="mt-2 grid grid-cols-3 gap-2">
        {options.map((o) => (
          <label key={o}
            className={`cursor-pointer rounded-lg border px-3 py-2 text-center text-sm focus-within:ring-2 focus-within:ring-teal-600 ${value === o ? 'border-teal-600 bg-teal-50 font-medium text-teal-900' : 'border-stone-300 text-stone-700 hover:bg-stone-50'}`}>
            <input type="radio" name={name} value={o} checked={value === o} onChange={() => onChange(o)} className="sr-only" />
            {labels[o]}
          </label>
        ))}
      </div>
    </fieldset>
  )
}

export default function CheckinForm({ date, onDateChange, onSaved }: Props) {
  const today = todayLocal()
  const [feeling, setFeeling] = useState<NewFeeling | ''>('')
  const [energy, setEnergy] = useState<EnergyLevel | ''>('')
  const [symptoms, setSymptoms] = useState('')
  const [notes, setNotes] = useState('')
  const [existing, setExisting] = useState<Checkin | null>(null)
  const [loadingExisting, setLoadingExisting] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  /** Puts a saved check-in (or an empty form) into the fields. */
  const fill = (c: Checkin | null) => {
    setExisting(c)
    setFeeling(c && (FEELINGS as string[]).includes(c.feeling) ? (c.feeling as NewFeeling) : '')   // an old-style answer is not pre-selected
    setEnergy(c?.energyLevel ?? '')
    setSymptoms(c?.symptoms ?? '')
    setNotes(c?.notes ?? '')
  }

  // Whenever the date changes, look up whether that day already has a check-in and pre-fill the form with it.
  useEffect(() => {
    let cancelled = false
    setLoadingExisting(true)
    setError('')
    setSuccess('')
    getCheckinForDate(date)
      .then((c) => { if (!cancelled) fill(c) })
      .catch((e) => { if (!cancelled) { fill(null); setError(getErrorMessage(e)) } })
      .finally(() => { if (!cancelled) setLoadingExisting(false) })
    return () => { cancelled = true }
  }, [date])

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!feeling || !energy) {
      setError('Please choose how you feel overall and your energy level.')
      return
    }
    setBusy(true)
    setError('')
    setSuccess('')
    try {
      const { checkin, created } = await saveCheckin({
        date, feeling, energyLevel: energy, symptoms: symptoms.trim() || null, notes: notes.trim() || null,
      })
      fill(checkin)
      setSuccess(created ? `Check-in saved for ${formatDateLabel(date)}.` : `Check-in for ${formatDateLabel(date)} updated.`)
      onSaved()
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const legacyAnswer = existing && !(FEELINGS as string[]).includes(existing.feeling)
  const disabled = busy || loadingExisting

  return (
    <section id="checkin-form" className="space-y-4 rounded-2xl border border-stone-200 bg-white p-6" aria-label="Daily well-being check-in">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-xl font-semibold text-stone-900">Daily well-being check-in</h2>
        <label className="text-sm text-stone-600">
          Day{' '}
          <input type="date" value={date} max={today} required
            onChange={(e) => e.target.value && onDateChange(e.target.value)}
            className="rounded-lg border border-stone-300 px-2 py-1" />
        </label>
      </div>

      {loadingExisting && <p className="text-sm text-stone-600">Loading this day’s check-in…</p>}
      {!loadingExisting && existing && (
        <p className="text-sm text-stone-700">
          You already checked in for {formatDateLabel(date)}. Saving will <b>update</b> that check-in.
        </p>
      )}
      {legacyAnswer && existing && (
        <p className="text-sm text-amber-800">
          This day was saved with an older answer (“{FEELING_LABEL[existing.feeling]}”). Please pick one of the three options below.
        </p>
      )}

      <form onSubmit={submit} className="space-y-4">
        <Choice legend="How do you feel overall?" name="feeling" options={FEELINGS} labels={FEELING_LABEL} value={feeling} onChange={setFeeling} />
        <Choice legend="Energy level" name="energy" options={ENERGIES} labels={ENERGY_LABEL} value={energy} onChange={setEnergy} />

        <label className="block text-sm font-medium text-stone-700">
          Symptoms or concerns <span className="font-normal text-stone-500">(optional)</span>
          <input value={symptoms} maxLength={500} onChange={(e) => setSymptoms(e.target.value)}
            className="mt-1 w-full rounded-lg border border-stone-300 px-3 py-2 font-normal" />
        </label>
        <label className="block text-sm font-medium text-stone-700">
          Notes <span className="font-normal text-stone-500">(optional)</span>
          <textarea value={notes} maxLength={500} rows={3} onChange={(e) => setNotes(e.target.value)}
            className="mt-1 w-full rounded-lg border border-stone-300 px-3 py-2 font-normal" />
        </label>

        {error && <Alert>{error}</Alert>}
        {success && <Alert kind="success">{success}</Alert>}

        <button disabled={disabled}
          className="rounded-lg bg-teal-700 px-4 py-2 font-medium text-white hover:bg-teal-800 disabled:opacity-60">
          {busy ? 'Saving…' : existing ? 'Update check-in' : 'Save check-in'}
        </button>
      </form>
    </section>
  )
}

import { useState } from 'react'
import type { FormEvent } from 'react'
import { createMedication, updateMedication } from '../api/medications'
import { getErrorMessage } from '../api/errors'
import { FOOD_LABEL, TYPE_LABEL, UNIT_BY_TYPE, UNIT_LABEL } from '../utils/format'
import type { FoodInstruction, Medication, MedicationPayload, MedicationType } from '../types'
import Alert from './Alert'

interface Props {
  initial?: Medication         // present = edit mode
  onSaved: () => void
  onCancel: () => void
}

const inputClass = 'mt-1 w-full rounded-lg border border-stone-300 px-3 py-2'

export default function MedicationForm({ initial, onSaved, onCancel }: Props) {
  const editing = initial !== undefined
  const [name, setName] = useState(initial?.name ?? '')
  const [type, setType] = useState<MedicationType>(initial?.type ?? 'TABLET')
  const [strength, setStrength] = useState(initial?.strength ?? '')
  const [dosage, setDosage] = useState(String(initial?.dosagePerDose ?? 1))
  const [frequency, setFrequency] = useState(initial?.frequencyLabel ?? 'Twice daily')
  const [food, setFood] = useState<FoodInstruction>(initial?.foodInstruction ?? 'ANY_TIME')
  const [startDate, setStartDate] = useState(initial?.startDate ?? new Date().toISOString().slice(0, 10))
  const [endDate, setEndDate] = useState(initial?.endDate ?? '')
  const [notes, setNotes] = useState(initial?.notes ?? '')
  // only used when adding a new medication
  const [quantity, setQuantity] = useState('')
  const [threshold, setThreshold] = useState('5')
  const [times, setTimes] = useState<string[]>(['08:00', '20:00'])

  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const unit = UNIT_LABEL[UNIT_BY_TYPE[type]]

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    const payload: MedicationPayload = {
      name,
      type,
      strength: strength.trim() || null,
      dosagePerDose: Number(dosage),
      frequencyLabel: frequency,
      foodInstruction: food,
      notes: notes.trim() || null,
      startDate,
      endDate: endDate || null,
    }
    try {
      if (initial) {
        await updateMedication(initial.id, payload)
      } else {
        await createMedication({
          ...payload,
          initialQuantity: Number(quantity),
          lowStockThreshold: Number(threshold),
          doseTimes: times.filter((t) => t !== ''),
        })
      }
      onSaved()
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4 rounded-2xl border border-teal-200 bg-white p-6">
      <h3 className="text-lg font-semibold text-stone-900">{editing ? `Edit ${initial.name}` : 'Add a medicine'}</h3>

      <div className="grid gap-4 sm:grid-cols-2">
        <label className="block text-sm font-medium text-stone-700 sm:col-span-2">
          Medicine name
          <input required maxLength={120} value={name} onChange={(e) => setName(e.target.value)} className={inputClass} />
        </label>
        <label className="block text-sm font-medium text-stone-700">
          Type
          <select value={type} onChange={(e) => setType(e.target.value as MedicationType)} className={inputClass}>
            {(Object.keys(TYPE_LABEL) as MedicationType[])
              // a saved medicine cannot switch between tablet/capsule and syrup (its stock is stored differently)
              .filter((t) => !initial || (t === 'SYRUP') === (initial.type === 'SYRUP'))
              .map((t) => <option key={t} value={t}>{TYPE_LABEL[t]}</option>)}
          </select>
        </label>
        <label className="block text-sm font-medium text-stone-700">
          Strength <span className="font-normal text-stone-500">(optional, e.g. 500 mg)</span>
          <input maxLength={50} value={strength} onChange={(e) => setStrength(e.target.value)} className={inputClass} />
        </label>
        <label className="block text-sm font-medium text-stone-700">
          Amount per dose ({unit})
          <input type="number" required min="0.01" step="0.01" value={dosage} onChange={(e) => setDosage(e.target.value)} className={inputClass} />
        </label>
        <label className="block text-sm font-medium text-stone-700">
          How often
          <input required maxLength={60} value={frequency} onChange={(e) => setFrequency(e.target.value)} className={inputClass} />
        </label>
        <label className="block text-sm font-medium text-stone-700">
          Food instruction
          <select value={food} onChange={(e) => setFood(e.target.value as FoodInstruction)} className={inputClass}>
            {(Object.keys(FOOD_LABEL) as FoodInstruction[]).map((f) => <option key={f} value={f}>{FOOD_LABEL[f]}</option>)}
          </select>
        </label>
        <div className="grid grid-cols-2 gap-3">
          <label className="block text-sm font-medium text-stone-700">
            Start date
            <input type="date" required value={startDate} onChange={(e) => setStartDate(e.target.value)} className={inputClass} />
          </label>
          <label className="block text-sm font-medium text-stone-700">
            End date <span className="font-normal text-stone-500">(optional)</span>
            <input type="date" value={endDate} min={startDate} onChange={(e) => setEndDate(e.target.value)} className={inputClass} />
          </label>
        </div>
        <label className="block text-sm font-medium text-stone-700 sm:col-span-2">
          Notes <span className="font-normal text-stone-500">(optional)</span>
          <textarea maxLength={500} rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} className={inputClass} />
        </label>
      </div>

      {!editing && (
        <fieldset className="space-y-4 rounded-xl bg-stone-50 p-4">
          <legend className="px-1 text-sm font-medium text-stone-700">Stock and dose times</legend>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-sm font-medium text-stone-700">
              How many {unit} do you have?
              <input type="number" required min="0" step={type === 'SYRUP' ? 0.01 : 1} max={type === 'SYRUP' ? undefined : 200} value={quantity} onChange={(e) => setQuantity(e.target.value)} className={inputClass} />
            </label>
            <label className="block text-sm font-medium text-stone-700">
              Warn me when {unit} drop to
              <input type="number" required min="0" step={type === 'SYRUP' ? 0.01 : 1} value={threshold} onChange={(e) => setThreshold(e.target.value)} className={inputClass} />
            </label>
          </div>
          <div>
            <p className="text-sm font-medium text-stone-700">Dose times</p>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              {times.map((t, i) => (
                <span key={i} className="flex items-center gap-1">
                  <input type="time" required value={t} aria-label={`Dose time ${i + 1}`}
                    onChange={(e) => setTimes(times.map((x, j) => (j === i ? e.target.value : x)))}
                    className="rounded-lg border border-stone-300 px-2 py-1" />
                  <button type="button" onClick={() => setTimes(times.filter((_, j) => j !== i))}
                    aria-label={`Remove dose time ${i + 1}`} className="text-stone-500 hover:text-red-700">×</button>
                </span>
              ))}
              {times.length < 10 && (
                <button type="button" onClick={() => setTimes([...times, '12:00'])} className="text-sm text-teal-800 underline">
                  + Add time
                </button>
              )}
            </div>
          </div>
        </fieldset>
      )}

      {error && <Alert>{error}</Alert>}
      <div className="flex gap-3">
        <button disabled={busy} className="rounded-lg bg-teal-700 px-4 py-2 font-medium text-white hover:bg-teal-800 disabled:opacity-60">
          {busy ? 'Saving…' : editing ? 'Save changes' : 'Add medicine'}
        </button>
        <button type="button" onClick={onCancel} className="rounded-lg border border-stone-300 px-4 py-2 hover:bg-stone-100">Cancel</button>
      </div>
    </form>
  )
}

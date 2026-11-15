import { useState } from 'react'
import type { FormEvent } from 'react'
import { updateInventory } from '../api/medications'
import { getErrorMessage } from '../api/errors'
import { UNIT_LABEL } from '../utils/format'
import type { Medication } from '../types'
import Alert from './Alert'

interface Props {
  medication: Medication
  editable: boolean
  onChanged: () => void
}

/** Physical stock only: how much medicine is in the box. Not the same as doses taken. */
export default function StockPanel({ medication, editable, onChanged }: Props) {
  const inv = medication.inventory
  const [editing, setEditing] = useState(false)
  const [current, setCurrent] = useState(String(inv?.currentQuantity ?? 0))
  const [threshold, setThreshold] = useState(String(inv?.lowStockThreshold ?? 0))
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  if (!inv) return <p className="text-sm text-stone-500">No stock information.</p>

  const wholeCount = medication.type !== 'SYRUP'   // tablets/capsules are counted one by one on the strip
  const percent = inv.totalQuantity > 0 ? Math.min(100, (inv.currentQuantity / inv.totalQuantity) * 100) : 0
  const unit = UNIT_LABEL[inv.unit]

  const save = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await updateInventory(medication.id, Number(current), Number(threshold))
      setEditing(false)
      onChanged()
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between">
        <h4 className="text-sm font-medium text-stone-700">Stock in hand</h4>
        {inv.lowStock && (
          <span className="rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-medium text-red-800">Low stock</span>
        )}
      </div>
      <p className="mt-1 text-sm text-stone-800">
        <b>{inv.currentQuantity}</b> of {inv.totalQuantity} {unit} left
        <span className="text-stone-500"> · low at {inv.lowStockThreshold} or less</span>
      </p>
      <div className="mt-2 h-2 overflow-hidden rounded-full bg-stone-200" aria-hidden="true">
        <div className={`h-full ${inv.lowStock ? 'bg-red-500' : 'bg-teal-600'}`} style={{ width: `${percent}%` }} />
      </div>

      {editable && !editing && (
        <button onClick={() => setEditing(true)} className="mt-3 text-sm text-teal-800 underline">Update stock</button>
      )}
      {editable && editing && (
        <form onSubmit={save} className="mt-3 flex flex-wrap items-end gap-3">
          <label className="text-sm text-stone-700">
            Now in the box ({unit})
            <input type="number" min="0" step={wholeCount ? 1 : 0.01} max={wholeCount ? 200 : undefined} required value={current} onChange={(e) => setCurrent(e.target.value)}
              className="mt-1 block w-32 rounded-lg border border-stone-300 px-2 py-1" />
          </label>
          <label className="text-sm text-stone-700">
            Warn me at or below
            <input type="number" min="0" step={wholeCount ? 1 : 0.01} required value={threshold} onChange={(e) => setThreshold(e.target.value)}
              className="mt-1 block w-32 rounded-lg border border-stone-300 px-2 py-1" />
          </label>
          <button disabled={busy} className="rounded-lg bg-teal-700 px-3 py-1.5 text-sm font-medium text-white hover:bg-teal-800 disabled:opacity-60">Save</button>
          <button type="button" onClick={() => setEditing(false)} className="rounded-lg border border-stone-300 px-3 py-1.5 text-sm hover:bg-stone-100">Cancel</button>
        </form>
      )}
      {editable && editing && wholeCount && (
        <p className="mt-2 text-xs text-stone-500">
          This is the number of tablets in the box. Raising it adds empty compartments to the strip, lowering it removes empty ones.
          Tablets already taken stay on the strip.
        </p>
      )}
      {error && <div className="mt-2"><Alert>{error}</Alert></div>}
    </div>
  )
}

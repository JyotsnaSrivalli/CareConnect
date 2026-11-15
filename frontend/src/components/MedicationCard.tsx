import { useState } from 'react'
import { deleteMedication } from '../api/medications'
import { getErrorMessage } from '../api/errors'
import { FOOD_LABEL, TYPE_LABEL, UNIT_LABEL } from '../utils/format'
import type { Medication } from '../types'
import Alert from './Alert'
import ScheduleList from './ScheduleList'
import StockPanel from './StockPanel'

interface Props {
  medication: Medication
  editable: boolean            // false on the child dashboard: no edit/delete/add controls
  onChanged: () => void
  onEdit?: (medication: Medication) => void
}

export default function MedicationCard({ medication: m, editable, onChanged, onEdit }: Props) {
  const [error, setError] = useState('')

  const remove = async () => {
    if (!window.confirm(`Remove ${m.name}? It will disappear from your list.`)) return
    try {
      await deleteMedication(m.id)
      onChanged()
    } catch (err) {
      setError(getErrorMessage(err))
    }
  }

  return (
    <article className="rounded-2xl border border-stone-200 bg-white p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="text-lg font-semibold text-stone-900">
            {m.name} {m.strength && <span className="font-normal text-stone-600">· {m.strength}</span>}
          </h3>
          <p className="text-sm text-stone-600">
            {TYPE_LABEL[m.type]} · {m.dosagePerDose} {UNIT_LABEL[m.quantityUnit]} per dose · {m.frequencyLabel} · {FOOD_LABEL[m.foodInstruction]}
          </p>
          <p className="text-xs text-stone-500">
            From {m.startDate}{m.endDate ? ` to ${m.endDate}` : ''}
          </p>
        </div>
        {editable && (
          <div className="flex gap-2">
            <button onClick={() => onEdit?.(m)} className="rounded-lg border border-stone-300 px-3 py-1.5 text-sm hover:bg-stone-100">Edit</button>
            <button onClick={remove} className="rounded-lg border border-red-300 px-3 py-1.5 text-sm text-red-800 hover:bg-red-50">Delete</button>
          </div>
        )}
      </div>

      {m.notes && <p className="mt-3 rounded-lg bg-stone-50 p-3 text-sm text-stone-700">{m.notes}</p>}
      {error && <div className="mt-3"><Alert>{error}</Alert></div>}

      <div className="mt-4 grid gap-5 sm:grid-cols-2">
        <ScheduleList medication={m} editable={editable} onChanged={onChanged} />
        <StockPanel medication={m} editable={editable} onChanged={onChanged} />
      </div>
    </article>
  )
}

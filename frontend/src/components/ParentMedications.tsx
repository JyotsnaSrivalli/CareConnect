import { useEffect, useState } from 'react'
import { useMedications } from '../hooks/useMedications'
import type { Medication } from '../types'
import Alert from './Alert'
import MedicationCard from './MedicationCard'
import MedicationForm from './MedicationForm'

interface Props {
  version: number          // bumped by the dashboard when doses/stock changed elsewhere
  onChanged: () => void    // tell the dashboard that medicines/schedules/stock changed
}

export default function ParentMedications({ version, onChanged }: Props) {
  const { medications, loading, error, reload } = useMedications()
  const [adding, setAdding] = useState(false)
  const [editing, setEditing] = useState<Medication | null>(null)

  // reload whenever the dashboard says something changed (our own changes call onChanged, which bumps it)
  useEffect(() => {
    if (version > 0) void reload()
  }, [version, reload])

  const done = () => {
    setAdding(false)
    setEditing(null)
    onChanged()
  }

  return (
    <section className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold text-stone-900">My medicines</h2>
        {!adding && !editing && (
          <button onClick={() => setAdding(true)} className="rounded-lg bg-teal-700 px-4 py-2 font-medium text-white hover:bg-teal-800">
            Add medicine
          </button>
        )}
      </div>

      {error && <Alert>{error}</Alert>}
      {adding && <MedicationForm onSaved={done} onCancel={() => setAdding(false)} />}
      {editing && <MedicationForm key={editing.id} initial={editing} onSaved={done} onCancel={() => setEditing(null)} />}

      {loading && <p className="text-sm text-stone-600">Loading…</p>}
      {!loading && medications.length === 0 && !adding && (
        <p className="rounded-2xl border border-dashed border-stone-300 p-6 text-stone-600">
          No medicines yet. Click “Add medicine” to get started.
        </p>
      )}

      {medications.map((m) => (
        <MedicationCard key={m.id} medication={m} editable onChanged={onChanged} onEdit={setEditing} />
      ))}
    </section>
  )
}

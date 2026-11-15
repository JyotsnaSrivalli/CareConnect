import { useMedications } from '../hooks/useMedications'
import Alert from './Alert'
import MedicationCard from './MedicationCard'

/** Read-only view of an approved parent's medicines (no edit, delete or add controls). */
export default function ChildMedications({ parentId, parentName }: { parentId: number; parentName: string }) {
  const { medications, loading, error, reload } = useMedications(parentId)

  return (
    <div className="mt-4 space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="font-medium text-stone-800">{parentName}’s medicines</h3>
        <button onClick={() => void reload()} className="text-sm text-teal-800 underline">Refresh</button>
      </div>
      {error && <Alert>{error}</Alert>}
      {loading && <p className="text-sm text-stone-600">Loading…</p>}
      {!loading && !error && medications.length === 0 && (
        <p className="text-sm text-stone-600">{parentName} has not added any medicines yet.</p>
      )}
      {medications.map((m) => (
        <MedicationCard key={m.id} medication={m} editable={false} onChanged={() => void reload()} />
      ))}
    </div>
  )
}

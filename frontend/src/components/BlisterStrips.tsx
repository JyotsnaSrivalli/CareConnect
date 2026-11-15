import { useEffect, useRef, useState } from 'react'
import { takeTablet, undoTablet } from '../api/strips'
import { getErrorMessage } from '../api/errors'
import { useStrips } from '../hooks/useStrips'
import type { Tablet } from '../types'
import Alert from './Alert'
import TabletStrip from './TabletStrip'

interface Props {
  editable: boolean            // true = parent (clickable), false = child (read-only)
  parentId?: number            // child only
  parentName?: string          // child only, for the heading
  version?: number             // changes when stock was edited elsewhere -> reload
  onChanged?: () => void       // tell the page that stock changed
}

export default function BlisterStrips({ editable, parentId, parentName, version = 0, onChanged }: Props) {
  const { strips, loading, error, reload } = useStrips(parentId)
  const [actionError, setActionError] = useState('')
  const [busyTabletId, setBusyTabletId] = useState<number | null>(null)
  const inFlight = useRef(false)   // set immediately, so a fast double click can never start two requests

  useEffect(() => {
    if (version > 0) void reload()
  }, [version, reload])

  // The UI only changes after the server confirms: call the API, then re-read everything from the server.
  const run = async (tablet: Tablet, action: () => Promise<unknown>) => {
    if (inFlight.current) return
    inFlight.current = true
    setBusyTabletId(tablet.id)
    setActionError('')
    try {
      await action()
      await reload()
      onChanged?.()
    } catch (e) {
      setActionError(getErrorMessage(e))
      await reload()               // show the real state (e.g. that tablet was already taken elsewhere)
    } finally {
      inFlight.current = false
      setBusyTabletId(null)
    }
  }

  const onTake = (tablet: Tablet) => void run(tablet, () => takeTablet(tablet.id))
  const onUndo = (tablet: Tablet) => {
    if (!window.confirm(`Undo tablet ${tablet.slotNumber}? It becomes available again and one tablet is added back to your stock.`)) return
    void run(tablet, () => undoTablet(tablet.id))
  }

  if (!loading && !error && !actionError && strips.length === 0) return null   // e.g. only syrup, or no medicines yet

  return (
    <section className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold text-stone-900">{parentName ? `${parentName}’s medicine strips` : 'Medicine strips'}</h2>
        <button onClick={() => void reload()} className="text-sm text-teal-800 underline">Refresh</button>
      </div>
      {editable && strips.length > 0 && (
        <p className="text-sm text-stone-600">Click a tablet when you take it. Click a green one to undo a mistake.</p>
      )}
      {(error || actionError) && <Alert>{error || actionError}</Alert>}
      {loading && <p className="text-sm text-stone-600">Loading…</p>}

      {strips.map((strip) => (
        <TabletStrip key={strip.medicationId} strip={strip} editable={editable}
          busyTabletId={busyTabletId} onTake={onTake} onUndo={onUndo} />
      ))}
    </section>
  )
}

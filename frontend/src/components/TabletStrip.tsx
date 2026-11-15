import { formatTime, UNIT_LABEL } from '../utils/format'
import type { Strip, Tablet } from '../types'
import { FOIL_STYLE } from './blisterStyles'
import TabletSlot from './TabletSlot'

interface Props {
  strip: Strip
  editable: boolean
  busyTabletId: number | null
  onTake: (tablet: Tablet) => void
  onUndo: (tablet: Tablet) => void
}

export default function TabletStrip({ strip, editable, busyTabletId, onTake, onUndo }: Props) {
  const unit = UNIT_LABEL[strip.unit]
  const locked = busyTabletId !== null
  const percentToday = strip.expectedToday > 0 ? Math.min(100, Math.round((strip.takenToday * 100) / strip.expectedToday)) : 0

  return (
    <article className="rounded-2xl border border-stone-200 bg-white p-4 sm:p-5">
      <header className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h3 className="text-base font-semibold text-stone-900">
            {strip.medicationName}{strip.strength && ` – ${strip.strength}`}
          </h3>
          <p className="text-xs text-stone-500">One physical strip · {strip.totalTablets} {unit}</p>
        </div>
        <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${strip.takenTablets > 0 ? 'bg-emerald-100 text-emerald-800' : 'bg-stone-100 text-stone-600'}`}>
          {strip.takenTablets} of {strip.totalTablets} taken
        </span>
      </header>

      {strip.tablets.length === 0 ? (
        <p className="mt-3 rounded-xl border border-dashed border-stone-300 p-4 text-sm text-stone-600">
          No {unit} on this strip. Use “Update stock” on the medicine below to add some.
        </p>
      ) : (
        <div className="mt-3 rounded-2xl p-3 sm:p-4" style={FOIL_STYLE}>
          <div className="grid min-w-0 grid-cols-5 gap-2 sm:grid-cols-10">
            {strip.tablets.map((tablet) => (
              <TabletSlot
                key={tablet.id}
                tablet={tablet}
                unit={strip.unit}
                editable={editable}
                busy={busyTabletId === tablet.id}
                locked={locked}
                onToggle={() => (tablet.status === 'TAKEN' ? onUndo(tablet) : onTake(tablet))}
              />
            ))}
          </div>
        </div>
      )}

      <footer className="mt-4 space-y-2 border-t border-stone-200 pt-3">
        <div className="flex items-baseline justify-between gap-3">
          <span className="text-sm font-medium text-stone-700">
            Remaining stock
            {strip.lowStock && <span className="ml-2 rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-800">Low stock</span>}
          </span>
          <span className="text-xl font-semibold text-stone-900">{strip.remainingTablets} {unit}</span>
        </div>

        <div>
          <p className="text-sm text-stone-700">
            Today: <b>{strip.takenToday}</b>{strip.expectedToday > 0 ? ` of ${strip.expectedToday}` : ''} {unit} taken
            {strip.scheduleTimes.length > 0 && (
              <span className="text-stone-500"> · scheduled at {strip.scheduleTimes.map(formatTime).join(', ')}</span>
            )}
          </p>
          {strip.expectedToday > 0 && (
            <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-stone-200" role="progressbar"
              aria-label={`Today's progress for ${strip.medicationName}`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={percentToday}>
              <div className="h-full bg-teal-600 transition-all" style={{ width: `${percentToday}%` }} />
            </div>
          )}
        </div>
      </footer>
    </article>
  )
}

import { formatDateTime } from '../utils/format'
import type { QuantityUnit, Tablet } from '../types'
import { PENDING_STYLE, TAKEN_STYLE } from './blisterStyles'
import { CheckIcon, PillIcon } from './PillIcon'

interface Props {
  tablet: Tablet
  unit: QuantityUnit
  editable: boolean       // false = child: shown, but not clickable
  busy: boolean           // this compartment's request is running
  locked: boolean         // some request is running: nothing else may be clicked
  onToggle: () => void    // available -> take, taken -> undo (the strip decides what to call)
}

/** One compartment = one physical tablet = one confirmation. */
export default function TabletSlot({ tablet, unit, editable, busy, locked, onToggle }: Props) {
  const taken = tablet.status === 'TAKEN'
  const when = taken && tablet.takenAt ? formatDateTime(tablet.takenAt) : ''
  const label = taken
    ? `Tablet ${tablet.slotNumber}, taken${when ? ` on ${when}` : ''}.${editable ? ' Click to undo.' : ''}`
    : `Tablet ${tablet.slotNumber}, available.${editable ? ' Click to mark as taken.' : ''}`

  const content = (
    <>
      {taken ? <CheckIcon className="h-7 w-7 text-emerald-600" /> : <PillIcon unit={unit} className="h-7 w-7" />}
      <span className={`text-lg font-bold leading-none ${taken ? 'text-emerald-700' : 'text-stone-900'}`}>{tablet.slotNumber}</span>
      <span className={`text-sm ${taken ? 'font-medium text-emerald-800' : 'text-stone-600'}`}>
        {busy ? 'Saving…' : taken ? 'Taken' : 'Available'}
      </span>
      {/* always one line tall so taken and available compartments stay the same size */}
      <span
  className="block h-4 w-full min-w-0 truncate text-[9px] leading-4 text-emerald-800"
  title={when}
>
  {busy ? '' : when}
</span>
    </>
  )

  const layout = 'flex min-w-0 min-h-24 w-full flex-col items-center justify-center gap-1 overflow-hidden rounded-2xl px-1 py-2 text-center'

  if (!editable) {
    return <div className={layout} style={taken ? TAKEN_STYLE : PENDING_STYLE} role="img" aria-label={label}>{content}</div>
  }

  return (
    <button
      type="button"
      onClick={onToggle}
      disabled={locked}                       // blocks double clicks and clicks on other compartments while saving
      aria-label={label}
      aria-pressed={taken}
      aria-busy={busy}
      style={taken ? TAKEN_STYLE : PENDING_STYLE}
      className={`${layout} transition focus:outline-none focus-visible:ring-4 focus-visible:ring-teal-500/50 enabled:cursor-pointer ${
        taken ? 'enabled:hover:brightness-95' : 'enabled:hover:-translate-y-0.5'
      } disabled:cursor-wait ${locked && !busy ? 'disabled:opacity-60' : ''}`}
    >
      {content}
    </button>
  )
}

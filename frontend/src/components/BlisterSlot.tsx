import { formatClock, formatTime } from '../utils/format'
import type { Dose } from '../types'
import { PENDING_STYLE, TAKEN_STYLE } from './blisterStyles'
import { CheckIcon, PillIcon } from './PillIcon'

interface Props {
  dose: Dose
  number: number          // 1, 2, 3 ... in time order for this medicine
  editable: boolean       // false = child: shown, but not clickable
  busy: boolean           // this slot's request is running
  locked: boolean         // some request is running: nothing else may be clicked
  onToggle: () => void    // pending -> mark taken, taken -> undo (the page decides what to call)
}

/** One tablet compartment = exactly one backend dose (one schedule on one day). */
export default function BlisterSlot({ dose, number, editable, busy, locked, onToggle }: Props) {
  const taken = dose.status === 'TAKEN'
  const time = formatTime(dose.scheduledTime)
  const label = taken
    ? `Dose ${number}, ${time}, taken${dose.takenAt ? ` at ${formatClock(dose.takenAt)}` : ''}.${editable ? ' Click to undo.' : ''}`
    : `Dose ${number}, ${time}, pending.${editable ? ' Click to mark as taken.' : ''}`

  const content = (
    <>
      {taken ? <CheckIcon className="h-8 w-8 text-emerald-600" /> : <PillIcon unit={dose.quantityUnit} className="h-8 w-8" />}
      <span className={`text-lg font-bold leading-none ${taken ? 'text-emerald-700' : 'text-stone-900'}`}>{number}</span>
      <span className="text-xs text-stone-600">{time}</span>
      <span className={`text-sm ${taken ? 'font-medium text-emerald-800' : 'text-stone-600'}`}>
        {busy ? 'Saving…' : taken ? 'Taken' : 'Pending'}
      </span>
      {/* always one line tall so taken and pending compartments stay the same size */}
      <span className="h-4 text-xs text-emerald-800">{taken && dose.takenAt && !busy ? `at ${formatClock(dose.takenAt)}` : ''}</span>
    </>
  )

  const layout = 'flex min-h-32 w-full flex-col items-center justify-center gap-1 rounded-[2rem] px-2 py-3 text-center'

  if (!editable) {
    return <div className={layout} style={taken ? TAKEN_STYLE : PENDING_STYLE} role="img" aria-label={label}>{content}</div>
  }

  return (
    <button
      type="button"
      onClick={onToggle}
      disabled={locked}                       // blocks double clicks and clicks on other slots while saving
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

import type { DoseSummary } from '../types'

export default function DoseProgress({ summary }: { summary: DoseSummary }) {
  const { takenDoses, totalDoses, pendingDoses, completionPercent } = summary
  return (
    <div>
      <p className="text-sm text-stone-800">
        <b>{takenDoses}</b> of {totalDoses} doses confirmed · {completionPercent}% completed
        {pendingDoses > 0 && <span className="text-stone-500"> · {pendingDoses} still to confirm</span>}
      </p>
      <div className="mt-2 h-3 overflow-hidden rounded-full bg-stone-200"
        role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={completionPercent}>
        <div className="h-full bg-teal-600 transition-all" style={{ width: `${completionPercent}%` }} />
      </div>
    </div>
  )
}

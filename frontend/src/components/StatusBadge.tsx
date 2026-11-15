import type { ConnectionStatus } from '../types'

const STYLES: Record<ConnectionStatus, { label: string; className: string }> = {
  PENDING: { label: 'Waiting for approval', className: 'bg-amber-100 text-amber-900' },
  APPROVED: { label: 'Connected', className: 'bg-teal-100 text-teal-900' },
  REJECTED: { label: 'Not connected', className: 'bg-stone-200 text-stone-700' },
}

export default function StatusBadge({ status }: { status: ConnectionStatus }) {
  const { label, className } = STYLES[status]
  return <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${className}`}>{label}</span>
}

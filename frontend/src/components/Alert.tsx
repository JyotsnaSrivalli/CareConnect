import type { ReactNode } from 'react'

export default function Alert({ kind = 'error', children }: { kind?: 'error' | 'success'; children: ReactNode }) {
  const styles = kind === 'error' ? 'bg-red-50 text-red-800 border-red-200' : 'bg-teal-50 text-teal-900 border-teal-200'
  return (
    <div role={kind === 'error' ? 'alert' : 'status'} className={`rounded-lg border px-4 py-3 text-sm ${styles}`}>
      {children}
    </div>
  )
}

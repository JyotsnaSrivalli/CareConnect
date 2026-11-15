import { useEffect, useState } from 'react'
import client from './api/client'

type Health = { app: string; database: string }

export default function App() {
  const [health, setHealth] = useState<Health | null>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    client.get<Health>('/health').then((r) => setHealth(r.data)).catch(() => setError(true))
  }, [])

  const ok = health?.app === 'UP' && health?.database === 'UP'

  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-50 p-6">
      <div className="w-full max-w-md rounded-2xl bg-white p-8 shadow">
        <h1 className="text-2xl font-semibold text-slate-900">CareConnect</h1>
        <p className="mt-1 text-slate-600">Setup check</p>
        <ul className="mt-6 space-y-2 text-sm">
          <li>Frontend: <b className="text-green-600">Running</b></li>
          <li>Backend: <b className={health ? 'text-green-600' : error ? 'text-red-600' : 'text-slate-500'}>
            {health ? health.app : error ? 'Unreachable' : 'Checking…'}</b></li>
          <li>Database: <b className={health?.database === 'UP' ? 'text-green-600' : 'text-slate-500'}>
            {health ? health.database : '—'}</b></li>
        </ul>
        {ok && <p className="mt-6 rounded-lg bg-green-50 p-3 text-green-800">All three layers are connected.</p>}
        {error && <p className="mt-6 rounded-lg bg-red-50 p-3 text-red-800">Backend not reachable. Is Spring Boot running on port 8080?</p>}
      </div>
    </main>
  )
}

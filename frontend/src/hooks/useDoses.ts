import { useCallback, useEffect, useState } from 'react'
import { getDoses, getSummary } from '../api/logs'
import { getErrorMessage } from '../api/errors'
import type { Dose, DoseSummary } from '../types'

/** Loads the doses and the progress summary for one day (always from the server). */
export function useDoses(date: string, parentId?: number) {
  const [doses, setDoses] = useState<Dose[]>([])
  const [summary, setSummary] = useState<DoseSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const reload = useCallback(async () => {
    try {
      const [d, s] = await Promise.all([getDoses(date, parentId), getSummary(date, parentId)])
      setDoses(d)
      setSummary(s)
      setError('')
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }, [date, parentId])

  useEffect(() => {
    void reload()
  }, [reload])

  return { doses, summary, loading, error, reload }
}

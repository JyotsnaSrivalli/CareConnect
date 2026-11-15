import { useCallback, useEffect, useRef, useState } from 'react'
import { getAdherenceSummary } from '../api/adherence'
import { getErrorMessage } from '../api/errors'
import type { AdherenceSummary } from '../types'

/** Loads the adherence summary for one date (always from the server). */
export function useAdherence(date: string, parentId?: number) {
  const [summary, setSummary] = useState<AdherenceSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const latest = useRef(0)   // if the person changes the date quickly, only the newest answer is used

  const reload = useCallback(async () => {
    const mine = ++latest.current
    setLoading(true)
    try {
      const data = await getAdherenceSummary(date, parentId)
      if (mine !== latest.current) return
      setSummary(data)
      setError('')
    } catch (e) {
      if (mine !== latest.current) return
      setSummary(null)
      setError(getErrorMessage(e))
    } finally {
      if (mine === latest.current) setLoading(false)
    }
  }, [date, parentId])

  useEffect(() => {
    void reload()
  }, [reload])

  return { summary, loading, error, reload }
}

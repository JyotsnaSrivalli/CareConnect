import { useCallback, useEffect, useState } from 'react'
import axios from 'axios'
import { getCheckins } from '../api/checkins'
import { getErrorMessage } from '../api/errors'
import type { Checkin } from '../types'

/**
 * Check-in history, newest first.
 * enabled = false means "do not even ask the server" (a child whose parent has not shared check-ins).
 * forbidden = true means the server answered 403: the parent has not shared check-ins (or access was removed).
 */
export function useCheckinHistory(parentId?: number, enabled = true) {
  const [checkins, setCheckins] = useState<Checkin[]>([])
  const [loading, setLoading] = useState(enabled)
  const [error, setError] = useState('')
  const [forbidden, setForbidden] = useState(false)

  const reload = useCallback(async () => {
    if (!enabled) {
      setLoading(false)
      return
    }
    try {
      setCheckins(await getCheckins(parentId))
      setError('')
      setForbidden(false)
    } catch (e) {
      if (axios.isAxiosError(e) && e.response?.status === 403) {
        setForbidden(true)
        setError('')
      } else {
        setError(getErrorMessage(e))
      }
    } finally {
      setLoading(false)
    }
  }, [parentId, enabled])

  useEffect(() => {
    void reload()
  }, [reload])

  return { checkins, loading, error, forbidden, reload }
}

import { useCallback, useEffect, useState } from 'react'
import { getStrips } from '../api/strips'
import { getErrorMessage } from '../api/errors'
import type { Strip } from '../types'

export function useStrips(parentId?: number) {
  const [strips, setStrips] = useState<Strip[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const reload = useCallback(async () => {
    try {
      setStrips(await getStrips(parentId))
      setError('')
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }, [parentId])

  useEffect(() => {
    void reload()
  }, [reload])

  return { strips, loading, error, reload }
}

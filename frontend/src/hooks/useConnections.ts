import { useCallback, useEffect, useState } from 'react'
import { listConnections } from '../api/family'
import { getErrorMessage } from '../api/errors'
import type { Connection } from '../types'

export function useConnections() {
  const [connections, setConnections] = useState<Connection[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const reload = useCallback(async () => {
    try {
      setConnections(await listConnections())
      setError('')
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  return { connections, loading, error, reload }
}

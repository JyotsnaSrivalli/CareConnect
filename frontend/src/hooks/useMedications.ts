import { useCallback, useEffect, useState } from 'react'
import { listMedications } from '../api/medications'
import { getErrorMessage } from '../api/errors'
import type { Medication } from '../types'

/** parentId is only passed on the child dashboard; a parent always gets their own list. */
export function useMedications(parentId?: number) {
  const [medications, setMedications] = useState<Medication[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const reload = useCallback(async () => {
    try {
      setMedications(await listMedications(parentId))
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

  return { medications, loading, error, reload }
}

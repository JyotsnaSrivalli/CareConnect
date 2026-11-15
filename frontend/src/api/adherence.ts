import client from './client'
import type { AdherenceSummary } from '../types'

/** Read-only. date "YYYY-MM-DD" (never in the future). parentId is only sent by a child. */
export const getAdherenceSummary = (date: string, parentId?: number) =>
  client
    .get<AdherenceSummary>('/adherence/summary', { params: { date, ...(parentId ? { parentId } : {}) } })
    .then((r) => r.data)

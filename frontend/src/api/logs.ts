import client from './client'
import { todayLocal } from '../utils/format'
import type { Dose, DoseSummary } from '../types'

/** parentId is only sent by a child; a parent always gets their own data (the server ignores any id). */
export const getDoses = (date: string, parentId?: number) => {
  if (!parentId && date === todayLocal()) {
    return client.get<Dose[]>('/medication-logs/today').then((r) => r.data)
  }
  return client.get<Dose[]>('/medication-logs', { params: { date, ...(parentId ? { parentId } : {}) } }).then((r) => r.data)
}

export const getSummary = (date: string, parentId?: number) =>
  client.get<DoseSummary>('/medication-logs/summary', { params: { date, ...(parentId ? { parentId } : {}) } }).then((r) => r.data)

export const markTaken = (scheduleId: number, date: string) =>
  client.post<Dose>(`/medication-logs/${scheduleId}/taken`, null, { params: { date } }).then((r) => r.data)

export const undoDose = (logId: number) => client.delete<Dose>(`/medication-logs/${logId}`).then((r) => r.data)

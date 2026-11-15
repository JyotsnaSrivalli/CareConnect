import axios from 'axios'
import client from './client'
import type { Checkin, CheckinPayload } from '../types'

/**
 * Parent only. One check-in per date: if one already exists for that date the server UPDATES it.
 * created = true when a new one was made (HTTP 201), false when an existing one was updated (HTTP 200).
 */
export const saveCheckin = (payload: CheckinPayload) =>
  client.post<Checkin>('/checkins', payload).then((r) => ({ checkin: r.data, created: r.status === 201 }))

/** Newest first. parentId is only sent by a child; a parent always gets their own history. */
export const getCheckins = (parentId?: number, limit?: number) =>
  client
    .get<Checkin[]>('/checkins', { params: { ...(parentId ? { parentId } : {}), ...(limit ? { limit } : {}) } })
    .then((r) => r.data)

/** One day's check-in, or null when there is none (the server answers 404 for "none"). */
export async function getCheckinForDate(date: string): Promise<Checkin | null> {
  try {
    return (await client.get<Checkin>(`/checkins/${date}`)).data
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 404) return null
    throw e
  }
}

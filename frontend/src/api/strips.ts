import client from './client'
import type { Strip } from '../types'

/** parentId is only sent by a child; a parent always gets their own strips (the server ignores any id). */
export const getStrips = (parentId?: number) =>
  client.get<Strip[]>('/strips', { params: parentId ? { parentId } : {} }).then((r) => r.data)

/** Marks ONE compartment as taken. The server records the time and removes one unit of stock. */
export const takeTablet = (tabletId: number) => client.post<Strip>(`/tablets/${tabletId}/take`).then((r) => r.data)

/** Makes that exact compartment available again and gives one unit of stock back. */
export const undoTablet = (tabletId: number) => client.post<Strip>(`/tablets/${tabletId}/undo`).then((r) => r.data)

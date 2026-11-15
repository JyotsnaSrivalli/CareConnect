import client from './client'
import type { Connection, Invite } from '../types'

export const createInvite = () => client.post<Invite>('/family/invite').then((r) => r.data)

export const connectWithCode = (code: string) =>
  client.post<Connection>('/family/connect', { code }).then((r) => r.data)

export const decide = (connectionId: number, approve: boolean, shareCheckins = false) =>
  client.post<Connection>('/family/approve', { connectionId, approve, shareCheckins }).then((r) => r.data)

export const listConnections = () => client.get<Connection[]>('/family/connections').then((r) => r.data)

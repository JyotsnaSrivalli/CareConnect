import client from './client'
import type { AuthResponse, Role, User } from '../types'

export interface RegisterPayload {
  name: string
  email: string
  password: string
  role: Role
}

export const register = (payload: RegisterPayload) =>
  client.post<AuthResponse>('/auth/register', payload).then((r) => r.data)

export const login = (email: string, password: string) =>
  client.post<AuthResponse>('/auth/login', { email, password }).then((r) => r.data)

export const me = () => client.get<User>('/auth/me').then((r) => r.data)

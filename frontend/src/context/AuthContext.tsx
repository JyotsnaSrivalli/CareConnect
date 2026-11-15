import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import * as authApi from '../api/auth'
import type { RegisterPayload } from '../api/auth'
import { TOKEN_KEY } from '../api/client'
import type { User } from '../types'

interface AuthState {
  user: User | null
  loading: boolean
  login: (email: string, password: string) => Promise<User>
  register: (payload: RegisterPayload) => Promise<User>
  logout: () => void
}

const AuthContext = createContext<AuthState | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  // If a token is stored we must check it with the server before deciding what to show
  const [loading, setLoading] = useState<boolean>(() => localStorage.getItem(TOKEN_KEY) !== null)

  useEffect(() => {
    if (!localStorage.getItem(TOKEN_KEY)) return
    let cancelled = false
    authApi
      .me()
      .then((u) => {
        if (!cancelled) setUser(u)
      })
      .catch(() => localStorage.removeItem(TOKEN_KEY))
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const value = useMemo<AuthState>(
    () => ({
      user,
      loading,
      login: async (email, password) => {
        const res = await authApi.login(email, password)
        localStorage.setItem(TOKEN_KEY, res.token)
        setUser(res.user)
        return res.user
      },
      register: async (payload) => {
        const res = await authApi.register(payload)
        localStorage.setItem(TOKEN_KEY, res.token)
        setUser(res.user)
        return res.user
      },
      logout: () => {
        localStorage.removeItem(TOKEN_KEY)
        setUser(null)
      },
    }),
    [user, loading],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}

import axios from 'axios'

export const TOKEN_KEY = 'token'

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api',
})

// Attach the JWT to every request
client.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// If the server says the token is invalid/expired, log out and go to /login
client.interceptors.response.use(
  (response) => response,
  (error) => {
    const url: string = error.config?.url ?? ''
    const isAuthAttempt = url.includes('/auth/login') || url.includes('/auth/register')
    if (error.response?.status === 401 && !isAuthAttempt) {
      localStorage.removeItem(TOKEN_KEY)
      if (window.location.pathname !== '/login') window.location.assign('/login')
    }
    return Promise.reject(error)
  },
)

export default client

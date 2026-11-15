import axios from 'axios'

/** Turns any thrown error into a friendly message for the UI. */
export function getErrorMessage(err: unknown): string {
  if (axios.isAxiosError(err)) {
    if (!err.response) return 'Cannot reach the server. Is the backend running?'
    const data = err.response.data as { message?: string; fieldErrors?: Record<string, string> } | undefined
    if (data?.fieldErrors) return Object.values(data.fieldErrors).join(' ')
    if (data?.message) return data.message
  }
  return 'Something went wrong. Please try again.'
}

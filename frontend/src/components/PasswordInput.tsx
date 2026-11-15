import { useId, useState } from 'react'
import type { ReactNode } from 'react'

interface Props {
  label: ReactNode                      // the text above the field (may contain a hint)
  value: string
  onChange: (value: string) => void
  autoComplete: 'current-password' | 'new-password'
  minLength?: number
  maxLength?: number
}

/**
 * A password field with a show / hide button. Only the input's `type` changes ("password" <-> "text").
 * The value is kept in the parent's state, so toggling never clears what was typed, and nothing about
 * how the password is sent to the server changes.
 */
export default function PasswordInput({ label, value, onChange, autoComplete, minLength, maxLength }: Props) {
  const id = useId()
  const [visible, setVisible] = useState(false)

  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-stone-700">{label}</label>
      <div className="relative mt-1">
        <input id={id} type={visible ? 'text' : 'password'} required autoComplete={autoComplete}
          minLength={minLength} maxLength={maxLength} value={value} onChange={(e) => onChange(e.target.value)}
          className="w-full rounded-lg border border-stone-300 py-2 pl-3 pr-11" />
        {/* type="button" so clicking it can never submit the form */}
        <button type="button" onClick={() => setVisible((v) => !v)}
          aria-label={visible ? 'Hide password' : 'Show password'} aria-pressed={visible}
          className="absolute inset-y-0 right-0 flex w-10 items-center justify-center rounded-r-lg text-stone-500 hover:text-stone-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-teal-600">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
            strokeLinecap="round" strokeLinejoin="round" className="h-5 w-5" aria-hidden="true">
            {visible ? (
  <>
    {/* Plain eye = password is visible */}
    <path d="M2.5 12C3.5 10.2 7 6 12 6s8.5 4.2 9.5 6c-1 1.8-4.5 6-9.5 6s-8.5-4.2-9.5-6z" />
    <circle cx="12" cy="12" r="3" />
  </>
) : (
  <>
    {/* Crossed-out eye = password is hidden */}
    <path d="M3 3l18 18" />
    <path d="M10.6 6.1A9.9 9.9 0 0 1 12 6c5 0 8.5 4.2 9.5 6a11.6 11.6 0 0 1-2.6 3.3M6.2 7.6A11.7 11.7 0 0 0 2.5 12c1 1.8 4.5 6 9.5 6 1.5 0 2.8-.4 4-1" />
    <path d="M9.9 10a3 3 0 0 0 4.1 4.1" />
  </>
)}
          </svg>
        </button>
      </div>
    </div>
  )
}

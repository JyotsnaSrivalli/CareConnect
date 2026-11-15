
import type { QuantityUnit } from '../types'

interface IconProps {
  className?: string
}

interface PillIconProps extends IconProps {
  unit: QuantityUnit
}

export function CheckIcon({ className = '' }: IconProps) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="9" />
      <path d="m8 12 2.5 2.5L16 9" />
    </svg>
  )
}

export function PillIcon({ className = '', unit }: PillIconProps) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      data-unit={unit}
    >
      <path d="M10.5 20.5a5.5 5.5 0 0 1-7.78-7.78l8-8a5.5 5.5 0 0 1 7.78 7.78l-8 8Z" />
      <path d="m8.5 8.5 7 7" />
    </svg>
  )
}

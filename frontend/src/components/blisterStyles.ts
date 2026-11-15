import type { CSSProperties } from 'react'

// Raised "blister" for something still to take, pressed-in green for something confirmed.
export const PENDING_STYLE: CSSProperties = {
  background: 'linear-gradient(180deg, #ffffff 0%, #eceff1 100%)',
  border: '1px solid #cfd4d9',
  boxShadow: '0 2px 3px rgba(15,23,42,.28), inset 0 2px 3px rgba(255,255,255,.95), inset 0 -5px 7px rgba(15,23,42,.07)',
}
export const TAKEN_STYLE: CSSProperties = {
  background: 'linear-gradient(180deg, #bbf7d0 0%, #d1fae5 100%)',
  border: '1px solid #059669',
  boxShadow: 'inset 0 4px 7px rgba(5,150,105,.28), 0 1px 1px rgba(255,255,255,.7)',
}
// Silver foil behind the compartments
export const FOIL_STYLE: CSSProperties = {
  background: 'linear-gradient(135deg, #e5e7eb 0%, #cbd5e1 48%, #e2e8f0 100%)',
  border: '1px solid #b8c0cb',
  boxShadow: 'inset 0 1px 2px rgba(255,255,255,.8), inset 0 -2px 4px rgba(15,23,42,.12)',
}

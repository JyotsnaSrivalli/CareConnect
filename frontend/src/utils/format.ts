import type { EnergyLevel, Feeling, FoodInstruction, MedicationType, QuantityUnit } from '../types'

/** "20:00:00" -> "8:00 PM" */
export function formatTime(time: string): string {
  const [h, m] = time.split(':')
  const hour = Number(h)
  const suffix = hour >= 12 ? 'PM' : 'AM'
  const hour12 = hour % 12 === 0 ? 12 : hour % 12
  return `${hour12}:${m} ${suffix}`
}

export const UNIT_LABEL: Record<QuantityUnit, string> = { TABLET: 'tablets', CAPSULE: 'capsules', ML: 'ml' }
export const UNIT_BY_TYPE: Record<MedicationType, QuantityUnit> = { TABLET: 'TABLET', CAPSULE: 'CAPSULE', SYRUP: 'ML' }
export const TYPE_LABEL: Record<MedicationType, string> = { TABLET: 'Tablet', CAPSULE: 'Capsule', SYRUP: 'Syrup' }
export const FOOD_LABEL: Record<FoodInstruction, string> = {
  BEFORE_FOOD: 'Before food',
  AFTER_FOOD: 'After food',
  WITH_FOOD: 'With food',
  ANY_TIME: 'Any time',
}

const UNIT_SINGULAR: Record<QuantityUnit, string> = { TABLET: 'tablet', CAPSULE: 'capsule', ML: 'ml' }

/** 1 -> "1 tablet", 2 -> "2 tablets", 5 ml -> "5 ml" */
export function formatAmount(amount: number, unit: QuantityUnit): string {
  return `${amount} ${amount === 1 ? UNIT_SINGULAR[unit] : UNIT_LABEL[unit]}`
}

/** Today's date in the browser's local time zone as YYYY-MM-DD (toISOString would use UTC). */
export function todayLocal(): string {
  return new Date().toLocaleDateString('en-CA')
}

/** "2026-11-03T10:15:00" -> "10:15 AM" */
export function formatClock(dateTime: string): string {
  return new Date(dateTime).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
}

/** "2026-11-03T08:07:00" -> "3 Nov, 8:07 AM" */
export function formatDateTime(dateTime: string): string {
  return new Date(dateTime).toLocaleString([], { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })
}

// ---------------- Day 5 ----------------

export const FEELING_LABEL: Record<Feeling, string> = {
  GOOD: 'Good',
  OKAY: 'Okay',
  NOT_GOOD: 'Not good',
  // older answers, shown only when an old entry is displayed
  VERY_GOOD: 'Very good',
  NORMAL: 'Normal',
  FEELING_LOW: 'Feeling low',
  FEELING_UNWELL: 'Feeling unwell',
}
export const ENERGY_LABEL: Record<EnergyLevel, string> = { HIGH: 'High', MODERATE: 'Moderate', LOW: 'Low' }

/** "2026-11-12" -> "12 Nov 2026". Built from the parts so the browser time zone can never shift the day. */
export function formatDateLabel(isoDate: string): string {
  const [y, m, d] = isoDate.split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString([], { day: 'numeric', month: 'short', year: 'numeric' })
}

export type Role = 'PARENT' | 'CHILD'
export type ConnectionStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface User {
  id: number
  name: string
  email: string
  role: Role
}

export interface AuthResponse {
  token: string
  user: User
}

export interface Invite {
  code: string
  expiresAt: string
}

export interface Connection {
  id: number
  status: ConnectionStatus
  shareCheckins: boolean
  createdAt: string
  approvedAt: string | null
  parent: User
  child: User
}

export type MedicationType = 'TABLET' | 'CAPSULE' | 'SYRUP'
export type QuantityUnit = 'TABLET' | 'CAPSULE' | 'ML'
export type FoodInstruction = 'BEFORE_FOOD' | 'AFTER_FOOD' | 'WITH_FOOD' | 'ANY_TIME'

export interface Schedule {
  id: number
  doseTime: string // "08:00:00"
}

export interface Inventory {
  totalQuantity: number
  currentQuantity: number
  lowStockThreshold: number
  unit: QuantityUnit
  lowStock: boolean
  updatedAt: string
}

export interface Medication {
  id: number
  parentId: number
  name: string
  type: MedicationType
  strength: string | null
  dosagePerDose: number
  quantityUnit: QuantityUnit
  frequencyLabel: string
  foodInstruction: FoodInstruction
  notes: string | null
  startDate: string
  endDate: string | null
  active: boolean
  createdAt: string
  schedules: Schedule[]
  inventory: Inventory | null
}

/** Body for POST/PUT /medications. The last three fields are only used when creating. */
export interface MedicationPayload {
  name: string
  type: MedicationType
  strength: string | null
  dosagePerDose: number
  frequencyLabel: string
  foodInstruction: FoodInstruction
  notes: string | null
  startDate: string
  endDate: string | null
  initialQuantity?: number
  lowStockThreshold?: number
  doseTimes?: string[]
}

export type DoseStatus = 'PENDING' | 'TAKEN'

/** One scheduled dose on one day. PENDING = not confirmed yet (never "missed"). */
export interface Dose {
  medicationId: number
  medicationName: string
  strength: string | null
  dosagePerDose: number
  quantityUnit: QuantityUnit
  scheduleId: number
  scheduledTime: string // "08:00:00"
  scheduledDate: string // "2026-11-03"
  status: DoseStatus
  logId: number | null
  takenAt: string | null
  remainingQuantity: number | null
  lowStock: boolean
}

export interface DoseSummary {
  date: string
  totalDoses: number
  takenDoses: number
  pendingDoses: number
  completionPercent: number
}

export type TabletStatus = 'AVAILABLE' | 'TAKEN'

/** One compartment of the blister strip = one physical tablet. */
export interface Tablet {
  id: number
  slotNumber: number
  status: TabletStatus
  takenAt: string | null // date + time of the confirmation
}

/** One strip = one tablet/capsule medicine. Everything here comes from the server. */
export interface Strip {
  medicationId: number
  medicationName: string
  strength: string | null
  type: MedicationType
  unit: QuantityUnit
  dosagePerDose: number
  totalTablets: number
  takenTablets: number
  remainingTablets: number
  lowStockThreshold: number
  lowStock: boolean
  scheduleTimes: string[] // "08:00:00"
  expectedToday: number
  takenToday: number
  tablets: Tablet[]
}

// ---------------- Day 5: daily well-being check-in ----------------

/** The form only offers the first three. The others are old values that can still appear in saved history. */
export type Feeling = 'GOOD' | 'OKAY' | 'NOT_GOOD' | 'VERY_GOOD' | 'NORMAL' | 'FEELING_LOW' | 'FEELING_UNWELL'
export type NewFeeling = 'GOOD' | 'OKAY' | 'NOT_GOOD'
export type EnergyLevel = 'HIGH' | 'MODERATE' | 'LOW'

export interface Checkin {
  id: number
  date: string // "2026-11-12"
  feeling: Feeling
  energyLevel: EnergyLevel | null // null only for rows saved before Day 5
  symptoms: string | null
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** Body for POST /checkins. There is no parent id: the server takes it from the login token. */
export interface CheckinPayload {
  date: string
  feeling: NewFeeling
  energyLevel: EnergyLevel
  symptoms: string | null
  notes: string | null
}

// ---------------- Day 5: medication adherence summary ----------------

/** What is physically left in the box. NOT the same thing as confirmed doses. */
export interface PhysicalStock {
  remaining: number
  unit: QuantityUnit
  lowStock: boolean
}

export interface AdherenceMedication {
  medicationId: number
  name: string
  strength: string | null
  type: MedicationType
  unit: string // "tablets" | "capsules" | "doses"
  dosagePerDose: number
  dosageUnit: QuantityUnit
  timesPerDay: number | null
  confirmed: number
  // The next five are null for a past date (the schedule history is not stored)
  expected: number | null
  remaining: number | null
  completionPercent: number | null
  extraConfirmations: number | null
  physicalStock: PhysicalStock | null
}

export interface AdherenceSummary {
  date: string
  today: boolean
  expectedAvailable: boolean
  message: string | null // explains why a past date has no expected numbers
  confirmedTotal: number
  expectedTotal: number | null
  remainingTotal: number | null
  completionPercent: number | null
  extraConfirmations: number | null
  medications: AdherenceMedication[]
}

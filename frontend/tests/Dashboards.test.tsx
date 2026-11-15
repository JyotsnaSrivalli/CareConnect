import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AdherenceSummary, Connection, Strip } from '../src/types'

// ---- fake backend modules (the REAL components run on top of them) ----
const api = vi.hoisted(() => ({
  getStrips: vi.fn(), takeTablet: vi.fn(), undoTablet: vi.fn(),
  getDoses: vi.fn(), getSummary: vi.fn(), markTaken: vi.fn(), undoDose: vi.fn(),
  getAdherenceSummary: vi.fn(),
  getCheckins: vi.fn(), getCheckinForDate: vi.fn(), saveCheckin: vi.fn(),
  connections: { current: [] as unknown[] },
}))
vi.mock('../src/api/strips', () => ({ getStrips: api.getStrips, takeTablet: api.takeTablet, undoTablet: api.undoTablet }))
vi.mock('../src/api/logs', () => ({ getDoses: api.getDoses, getSummary: api.getSummary, markTaken: api.markTaken, undoDose: api.undoDose }))
vi.mock('../src/api/adherence', () => ({ getAdherenceSummary: api.getAdherenceSummary }))
vi.mock('../src/api/checkins', () => ({ getCheckins: api.getCheckins, getCheckinForDate: api.getCheckinForDate, saveCheckin: api.saveCheckin }))
vi.mock('../src/context/AuthContext', () => ({ useAuth: () => ({ user: { id: 1, name: 'Asha', email: 'a@t.com', role: 'PARENT' } }) }))
vi.mock('../src/hooks/useConnections', () => ({
  useConnections: () => ({ connections: api.connections.current, loading: false, error: '', reload: vi.fn() }),
}))
// parts of the dashboards that this test does not care about
vi.mock('../src/components/ParentMedications', () => ({ default: () => <div>medicines section</div> }))
vi.mock('../src/components/InviteCodeCard', () => ({ default: () => <div>invite section</div> }))
vi.mock('../src/components/ParentConnections', () => ({ default: () => <div>connections section</div> }))
vi.mock('../src/components/LinkParentForm', () => ({ default: () => <div>link parent form</div> }))
vi.mock('../src/components/ChildMedications', () => ({ default: () => <div>child medicines section</div> }))

import ParentDashboard from '../src/pages/ParentDashboard'
import ChildDashboard from '../src/pages/ChildDashboard'

let strip: Strip
let summary: AdherenceSummary

const makeStrip = (): Strip => ({
  medicationId: 10, medicationName: 'Paracetamol', strength: '500 mg', type: 'TABLET', unit: 'TABLET', dosagePerDose: 1,
  totalTablets: 3, takenTablets: 0, remainingTablets: 3, lowStockThreshold: 1, lowStock: false, scheduleTimes: ['08:00:00', '20:00:00'],
  expectedToday: 2, takenToday: 0,
  tablets: [1, 2, 3].map((i) => ({ id: i, slotNumber: i, status: 'AVAILABLE' as const, takenAt: null })),
})
const makeSummary = (confirmed: number): AdherenceSummary => ({
  date: '2026-11-12', today: true, expectedAvailable: true, message: null,
  confirmedTotal: confirmed, expectedTotal: 2, remainingTotal: 2 - confirmed, completionPercent: confirmed * 50, extraConfirmations: 0,
  medications: [{ medicationId: 10, name: 'Paracetamol', strength: '500 mg', type: 'TABLET', unit: 'tablets', dosagePerDose: 1, dosageUnit: 'TABLET',
    timesPerDay: 2, confirmed, expected: 2, remaining: 2 - confirmed, completionPercent: confirmed * 50, extraConfirmations: 0,
    physicalStock: { remaining: 3 - confirmed, unit: 'TABLET', lowStock: false } }],
})
const connection = (shareCheckins: boolean, status: Connection['status'] = 'APPROVED'): Connection => ({
  id: 1, status, shareCheckins, createdAt: '2026-11-01T10:00:00', approvedAt: '2026-11-01T11:00:00',
  parent: { id: 3, name: 'Meera', email: 'm@t.com', role: 'PARENT' }, child: { id: 2, name: 'Ravi', email: 'r@t.com', role: 'CHILD' },
})

beforeEach(() => {
  strip = makeStrip()
  summary = makeSummary(0)
  api.getStrips.mockImplementation(async () => structuredClone([strip]))
  api.takeTablet.mockImplementation(async (id: number) => {
    strip = { ...strip, takenTablets: 1, remainingTablets: 2, takenToday: 1,
      tablets: strip.tablets.map((t) => (t.id === id ? { ...t, status: 'TAKEN' as const, takenAt: '2026-11-12T08:07:00' } : t)) }
    summary = makeSummary(1)                    // the "server" now reports 1 confirmed
    return strip
  })
  api.getDoses.mockResolvedValue([])
  api.getSummary.mockResolvedValue({ date: '2026-11-12', totalDoses: 0, takenDoses: 0, pendingDoses: 0, completionPercent: 0 })
  api.getAdherenceSummary.mockImplementation(async () => structuredClone(summary))
  api.getCheckins.mockResolvedValue([])
  api.getCheckinForDate.mockResolvedValue(null)
  api.connections.current = []
})
afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('parent dashboard', () => {
  it('shows the adherence summary, the tablet strip, the check-in form and the check-in history together', async () => {
    render(<ParentDashboard />)

    expect(await screen.findByRole('heading', { name: 'Welcome, Asha' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Today’s medication adherence' })).toBeInTheDocument()
    expect(await screen.findAllByRole('button', { name: /^Tablet \d+, available/ })).toHaveLength(3)       // Day 4 strip still here
    expect(screen.getByRole('heading', { name: 'Daily well-being check-in' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Previous check-ins' })).toBeInTheDocument()
    expect(screen.getByText('medicines section')).toBeInTheDocument()                                    // Day 3 still here
  })

  it('keeps the tablet strip working, and confirming a tablet refreshes the adherence summary', async () => {
    const user = userEvent.setup()
    render(<ParentDashboard />)
    expect(await screen.findByText(/0% completed/)).toBeInTheDocument()
    const callsBefore = api.getAdherenceSummary.mock.calls.length

    await user.click(await screen.findByRole('button', { name: /^Tablet 2, available/ }))

    expect(api.takeTablet).toHaveBeenCalledWith(2)
    expect(await screen.findByText(/50% completed/)).toBeInTheDocument()
    expect(api.getAdherenceSummary.mock.calls.length).toBeGreaterThan(callsBefore)
    expect(screen.getByText('Paracetamol', { selector: 'p' }).closest('li')).toHaveTextContent('1 of 2 tablets confirmed')
  })

  it('shows an error in the summary without breaking the rest of the dashboard', async () => {
    api.getAdherenceSummary.mockRejectedValue({ isAxiosError: true, response: { status: 500, data: { message: 'Summary failed' } } })
    render(<ParentDashboard />)

    expect(await screen.findByText('Summary failed')).toBeInTheDocument()
    expect(await screen.findAllByRole('button', { name: /^Tablet \d+, available/ })).toHaveLength(3)
    expect(screen.getByRole('heading', { name: 'Daily well-being check-in' })).toBeInTheDocument()
  })
})

describe('child dashboard (read-only)', () => {
  it('shows the parent’s summary and check-ins with no way to change anything', async () => {
    api.connections.current = [connection(true)]
    api.getCheckins.mockResolvedValue([{ id: 1, date: '2026-11-11', feeling: 'GOOD', energyLevel: 'HIGH', symptoms: null, notes: 'fine', createdAt: '', updatedAt: '' }])
    render(<ChildDashboard />)

    expect(await screen.findByRole('heading', { name: 'Meera’s medication adherence today' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Meera’s check-ins' })).toBeInTheDocument()
    expect(await screen.findByText('Feeling: Good')).toBeInTheDocument()
    expect(api.getAdherenceSummary).toHaveBeenCalledWith(expect.any(String), 3)
    expect(api.getCheckins).toHaveBeenCalledWith(3)

    // read-only: no check-in form, no save/edit buttons, tablets are not clickable
    expect(screen.queryByRole('heading', { name: 'Daily well-being check-in' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Save check-in|Update check-in|^Edit/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Tablet \d+, available/ })).not.toBeInTheDocument()
    expect(api.saveCheckin).not.toHaveBeenCalled()
    expect(api.takeTablet).not.toHaveBeenCalled()
  })

  it('shows a friendly message instead of check-ins when the parent has not enabled sharing, but still shows adherence', async () => {
    api.connections.current = [connection(false)]
    render(<ChildDashboard />)

    expect(await screen.findByText(/has not turned on check-in sharing/)).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Meera’s medication adherence today' })).toBeInTheDocument()
    expect(api.getCheckins).not.toHaveBeenCalled()
  })

  it('shows no parent data for a request that is pending or was removed', async () => {
    api.connections.current = [connection(true, 'PENDING'), { ...connection(true, 'REJECTED'), id: 2 }]
    render(<ChildDashboard />)

    expect(await screen.findByText(/Ask Meera to approve your request/)).toBeInTheDocument()
    expect(screen.getByText(/Access was declined or removed/)).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('heading', { name: /medication adherence/ })).not.toBeInTheDocument())
    expect(api.getAdherenceSummary).not.toHaveBeenCalled()
    expect(api.getCheckins).not.toHaveBeenCalled()
  })
})

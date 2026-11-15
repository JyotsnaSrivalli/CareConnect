import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AdherenceMedication, AdherenceSummary as Summary } from '../src/types'

const api = vi.hoisted(() => ({ getAdherenceSummary: vi.fn() }))
vi.mock('../src/api/adherence', () => api)

import AdherenceSummary from '../src/components/AdherenceSummary'
import { todayLocal } from '../src/utils/format'

function medication(over: Partial<AdherenceMedication> = {}): AdherenceMedication {
  return {
    medicationId: 10, name: 'Paracetamol', strength: '500 mg', type: 'TABLET', unit: 'tablets', dosagePerDose: 1, dosageUnit: 'TABLET',
    timesPerDay: 2, confirmed: 1, expected: 2, remaining: 1, completionPercent: 50, extraConfirmations: 0,
    physicalStock: { remaining: 11, unit: 'TABLET', lowStock: false }, ...over,
  }
}

function todaySummary(over: Partial<Summary> = {}): Summary {
  return {
    date: todayLocal(), today: true, expectedAvailable: true, message: null,
    confirmedTotal: 1, expectedTotal: 2, remainingTotal: 1, completionPercent: 50, extraConfirmations: 0,
    medications: [medication()], ...over,
  }
}

const PAST_MESSAGE = 'Expected doses and completion are only shown for today. CareConnect does not keep a history of past schedules.'
function pastSummary(): Summary {
  return {
    date: '2026-11-10', today: false, expectedAvailable: false, message: PAST_MESSAGE,
    confirmedTotal: 3, expectedTotal: null, remainingTotal: null, completionPercent: null, extraConfirmations: null,
    medications: [medication({ confirmed: 3, expected: null, remaining: null, completionPercent: null, extraConfirmations: null })],
  }
}

beforeEach(() => {
  api.getAdherenceSummary.mockImplementation(async (date: string) => (date === todayLocal() ? todaySummary() : pastSummary()))
})
afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('adherence summary: today', () => {
  it('shows a loading message first, then the totals, the progress bar and the per-medicine breakdown', async () => {
    render(<AdherenceSummary />)
    expect(screen.getByText('Loading…')).toBeInTheDocument()

    expect(await screen.findByText(/50% completed/)).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '50')
    expect(screen.getByRole('heading', { name: 'Today’s medication adherence' })).toBeInTheDocument()
    const line = screen.getByText('Paracetamol').closest('li')!
    expect(within(line).getByText(/1 of 2 tablets confirmed/)).toBeInTheDocument()
    expect(within(line).getByText(/1 still to confirm/)).toBeInTheDocument()
    expect(api.getAdherenceSummary).toHaveBeenCalledWith(todayLocal(), undefined)
  })

  it('keeps physical stock separate from confirmed doses', async () => {
    render(<AdherenceSummary />)
    const line = (await screen.findByText('Paracetamol')).closest('li')!
    expect(line).toHaveTextContent('1 of 2 tablets confirmed')
    expect(line).toHaveTextContent('Stock in the box: 11 tablets left')
  })

  it('labels each medicine with its own unit (syrup counts doses)', async () => {
    api.getAdherenceSummary.mockResolvedValue(todaySummary({
      confirmedTotal: 2, expectedTotal: 4, remainingTotal: 2,
      medications: [
        medication(),
        medication({ medicationId: 20, name: 'Cough syrup', type: 'SYRUP', unit: 'doses', dosageUnit: 'ML', dosagePerDose: 5,
          confirmed: 1, expected: 2, remaining: 1, physicalStock: { remaining: 95, unit: 'ML', lowStock: false } }),
      ],
    }))
    render(<AdherenceSummary />)
    const syrup = (await screen.findByText('Cough syrup')).closest('li')!
    expect(syrup).toHaveTextContent('1 of 2 doses confirmed')
    expect(syrup).toHaveTextContent('Stock in the box: 95 ml left')
  })

  it('flags extra confirmations and never shows more than 100%', async () => {
    api.getAdherenceSummary.mockResolvedValue(todaySummary({
      confirmedTotal: 3, expectedTotal: 2, remainingTotal: 0, completionPercent: 100, extraConfirmations: 1,
      medications: [medication({ confirmed: 3, expected: 2, remaining: 0, completionPercent: 100, extraConfirmations: 1 })],
    }))
    render(<AdherenceSummary />)
    expect(await screen.findByText(/1 confirmation more than expected today/)).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '100')
    expect(screen.queryByText(/still to confirm/)).not.toBeInTheDocument()   // remaining is 0, not negative
  })

  it('says so when nothing is scheduled (no percentage)', async () => {
    api.getAdherenceSummary.mockResolvedValue(todaySummary({
      confirmedTotal: 0, expectedTotal: 0, remainingTotal: 0, completionPercent: null, extraConfirmations: 0, medications: [],
    }))
    render(<AdherenceSummary />)
    expect(await screen.findByText(/Nothing is scheduled for this day yet/)).toBeInTheDocument()
    expect(screen.getByText(/No medicines are due today/)).toBeInTheDocument()
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
  })

  it('shows the server error message when loading fails', async () => {
    api.getAdherenceSummary.mockRejectedValue({ isAxiosError: true, response: { status: 500, data: { message: 'Database is down' } } })
    render(<AdherenceSummary />)
    expect(await screen.findByRole('alert')).toHaveTextContent('Database is down')
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
  })

  it('reloads from the server when the page says something changed (a tablet was confirmed)', async () => {
    const { rerender } = render(<AdherenceSummary version={0} />)
    await screen.findByText(/50% completed/)
    expect(api.getAdherenceSummary).toHaveBeenCalledTimes(1)

    api.getAdherenceSummary.mockResolvedValue(todaySummary({
      confirmedTotal: 2, remainingTotal: 0, completionPercent: 100,
      medications: [medication({ confirmed: 2, remaining: 0, completionPercent: 100 })],
    }))
    rerender(<AdherenceSummary version={1} />)

    expect(await screen.findByText(/100% completed/)).toBeInTheDocument()
    expect(api.getAdherenceSummary).toHaveBeenCalledTimes(2)
  })
})

describe('adherence summary: previous dates', () => {
  it('shows only confirmed counts and explains why expected and percentage are not available', async () => {
    render(<AdherenceSummary />)
    await screen.findByText(/50% completed/)

    fireEvent.change(screen.getByLabelText(/Day/), { target: { value: '2026-11-10' } })

    expect(await screen.findByText(PAST_MESSAGE)).toBeInTheDocument()
    expect(api.getAdherenceSummary).toHaveBeenLastCalledWith('2026-11-10', undefined)
    expect(screen.getByText('confirmed on this day', { exact: false })).toHaveTextContent('3 confirmed on this day')
    const line = screen.getByText('Paracetamol').closest('li')!
    expect(line).toHaveTextContent('3 tablets confirmed')
    expect(line).not.toHaveTextContent(' of ')              // no "x of y": nothing was "expected"
    expect(line).toHaveTextContent('Stock in the box right now: 11 tablets left')   // stock is today's, not that day's
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
    expect(screen.queryByText(/%/)).not.toBeInTheDocument()
    expect(screen.queryByText(/still to confirm/)).not.toBeInTheDocument()
  })

  it('can go back to today', async () => {
    render(<AdherenceSummary />)
    await screen.findByText(/50% completed/)
    fireEvent.change(screen.getByLabelText(/Day/), { target: { value: '2026-11-10' } })
    await screen.findByText(PAST_MESSAGE)

    fireEvent.click(screen.getByRole('button', { name: 'Back to today' }))

    expect(await screen.findByText(/50% completed/)).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByText(PAST_MESSAGE)).not.toBeInTheDocument())
  })

  it('says so when nothing was confirmed on that day', async () => {
    api.getAdherenceSummary.mockImplementation(async (date: string) => (date === todayLocal() ? todaySummary()
      : { ...pastSummary(), confirmedTotal: 0, medications: [] }))
    render(<AdherenceSummary />)
    await screen.findByText(/50% completed/)
    fireEvent.change(screen.getByLabelText(/Day/), { target: { value: '2026-11-10' } })
    expect(await screen.findByText('Nothing was confirmed on this day.')).toBeInTheDocument()
  })
})

describe('adherence summary: child (read-only)', () => {
  it('asks the server for the parent’s summary using the parent id, and has no buttons that change data', async () => {
    render(<AdherenceSummary parentId={3} parentName="Asha" />)
    expect(await screen.findByRole('heading', { name: 'Asha’s medication adherence today' })).toBeInTheDocument()
    expect(api.getAdherenceSummary).toHaveBeenCalledWith(todayLocal(), 3)
    expect(screen.queryByRole('button', { name: /taken|undo|confirm/i })).not.toBeInTheDocument()
  })

  it('shows the 403 message from the server when access is not allowed', async () => {
    api.getAdherenceSummary.mockRejectedValue({ isAxiosError: true, response: { status: 403, data: { message: 'You do not have access to this parent\'s information' } } })
    render(<AdherenceSummary parentId={3} parentName="Asha" />)
    expect(await screen.findByRole('alert')).toHaveTextContent('You do not have access')
  })
})

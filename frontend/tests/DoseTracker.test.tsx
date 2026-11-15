import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Dose, DoseSummary } from '../src/types'

// ---- a tiny fake backend: it owns the truth (status + stock), exactly like the real server ----
let doses: Dose[]
let releaseRequest: (() => void) | null
const api = vi.hoisted(() => ({
  getDoses: vi.fn(),
  getSummary: vi.fn(),
  markTaken: vi.fn(),
  undoDose: vi.fn(),
}))
vi.mock('../src/api/logs', () => api)

import DoseTracker from '../src/components/DoseTracker'

const NOW = '2026-11-03T08:07:00'

function makeDose(scheduleId: number, time: string): Dose {
  return {
    medicationId: 4, medicationName: 'Cough syrup', strength: '5 ml', dosagePerDose: 1, quantityUnit: 'ML',
    scheduleId, scheduledTime: time, scheduledDate: '2026-11-03', status: 'PENDING', logId: null, takenAt: null,
    remainingQuantity: 20, lowStock: false,
  }
}

function summary(): DoseSummary {
  const taken = doses.filter((d) => d.status === 'TAKEN').length
  return { date: '2026-11-03', totalDoses: doses.length, takenDoses: taken, pendingDoses: doses.length - taken,
    completionPercent: Math.round((taken * 100) / doses.length) }
}

beforeEach(() => {
  doses = [makeDose(7, '08:00:00'), makeDose(8, '20:00:00')]   // a twice-daily medicine = exactly 2 backend doses
  releaseRequest = null
  api.getDoses.mockImplementation(async () => structuredClone(doses))
  api.getSummary.mockImplementation(async () => summary())
  api.markTaken.mockImplementation(async (scheduleId: number) => {
    await new Promise<void>((resolve) => { releaseRequest = resolve })      // server is "thinking"
    const stock = doses[0].remainingQuantity! - 1
    doses = doses.map((d) => ({ ...d, remainingQuantity: stock,
      ...(d.scheduleId === scheduleId ? { status: 'TAKEN' as const, logId: 99, takenAt: NOW } : {}) }))
    return doses.find((d) => d.scheduleId === scheduleId)
  })
  api.undoDose.mockImplementation(async () => {
    const stock = doses[0].remainingQuantity! + 1
    doses = doses.map((d) => ({ ...d, remainingQuantity: stock,
      ...(d.logId === 99 ? { status: 'PENDING' as const, logId: null, takenAt: null } : {}) }))
  })
})
afterEach(() => { cleanup(); vi.clearAllMocks(); vi.restoreAllMocks() })

describe('syrup dose list (DoseTracker)', () => {
  it('shows exactly one row per backend dose (2) with the progress and the stock', async () => {
    render(<DoseTracker editable />)
    expect(await screen.findAllByRole('button', { name: 'Mark as taken' })).toHaveLength(2)
    expect(screen.getAllByText('Not confirmed yet')).toHaveLength(2)
    expect(screen.getByText(/doses confirmed · 0% completed/)).toBeInTheDocument()
    expect(screen.getAllByText(/20 ml left/)).toHaveLength(2)
  })

  it('stays "not confirmed" until the backend confirms, then shows taken and updates progress and stock', async () => {
    const user = userEvent.setup()
    render(<DoseTracker editable />)
    const [first] = await screen.findAllByRole('button', { name: 'Mark as taken' })

    await user.click(first)
    // request is running: still NOT shown as taken
    expect(api.markTaken).toHaveBeenCalledTimes(1)
    expect(api.markTaken).toHaveBeenCalledWith(7, expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/))
    expect(screen.queryByText(/^Taken/)).not.toBeInTheDocument()
    expect(screen.getByText('Saving…')).toBeInTheDocument()

    releaseRequest!()
    expect(await screen.findByText(/^Taken at/)).toBeInTheDocument()
    expect(screen.getByText(/doses confirmed · 50% completed/)).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '50')
    expect(screen.getAllByText(/19 ml left/)).toHaveLength(2)
    expect(screen.getByRole('button', { name: 'Undo' })).toBeInTheDocument()
  })

  it('blocks double clicks and clicks on the other dose while a request is running', async () => {
    const user = userEvent.setup()
    render(<DoseTracker editable />)
    const [first, second] = await screen.findAllByRole('button', { name: 'Mark as taken' })

    await user.dblClick(first)
    await user.click(second)
    expect(api.markTaken).toHaveBeenCalledTimes(1)          // one request only
    expect(first).toBeDisabled()

    releaseRequest!()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Mark as taken' })).toBeEnabled())
    expect(doses[0].remainingQuantity).toBe(19)             // deducted once
  })

  it('undo asks first, calls the undo API with the log id, and only then shows "not confirmed" again', async () => {
    const user = userEvent.setup()
    render(<DoseTracker editable />)
    await user.click((await screen.findAllByRole('button', { name: 'Mark as taken' }))[0])
    releaseRequest!()
    const undo = await screen.findByRole('button', { name: 'Undo' })

    vi.spyOn(window, 'confirm').mockReturnValue(false)
    await user.click(undo)
    expect(api.undoDose).not.toHaveBeenCalled()             // cancelled: nothing happens
    expect(screen.getByText(/^Taken at/)).toBeInTheDocument()

    vi.spyOn(window, 'confirm').mockReturnValue(true)
    await user.click(undo)
    expect(api.undoDose).toHaveBeenCalledWith(99)
    expect(await screen.findAllByText('Not confirmed yet')).toHaveLength(2)
    expect(screen.getByText(/doses confirmed · 0% completed/)).toBeInTheDocument()
    expect(screen.getAllByText(/20 ml left/)).toHaveLength(2)   // restored by the backend
  })

  it('keeps the dose "not confirmed" and shows the server message when the backend refuses', async () => {
    const user = userEvent.setup()
    api.markTaken.mockRejectedValueOnce({ isAxiosError: true, response: { status: 409, data: { message: 'Not enough stock' } } })
    render(<DoseTracker editable />)
    await user.click((await screen.findAllByRole('button', { name: 'Mark as taken' }))[0])

    expect(await screen.findByRole('alert')).toHaveTextContent('Not enough stock')
    expect(screen.getAllByText('Not confirmed yet')).toHaveLength(2)
    expect(screen.queryByText(/^Taken/)).not.toBeInTheDocument()
  })

  it('is read-only for a child: no buttons at all, and the parent id is sent to the server', async () => {
    render(<DoseTracker editable={false} parentId={3} parentName="Asha" />)
    expect(await screen.findAllByText('Not confirmed yet')).toHaveLength(2)
    expect(screen.queryByRole('button', { name: /Mark as taken|Undo/ })).not.toBeInTheDocument()
    expect(api.getDoses).toHaveBeenCalledWith(expect.any(String), 3)
  })

  it('shows completed doses again after a page reload because the state comes from the server', async () => {
    const user = userEvent.setup()
    const first = render(<DoseTracker editable />)
    await user.click((await screen.findAllByRole('button', { name: 'Mark as taken' }))[0])
    releaseRequest!()
    await screen.findByText(/^Taken at/)
    first.unmount()                                          // "refresh the browser"

    render(<DoseTracker editable />)
    expect(await screen.findByText(/^Taken at/)).toBeInTheDocument()
    expect(screen.getByText(/doses confirmed · 50% completed/)).toBeInTheDocument()
  })
})

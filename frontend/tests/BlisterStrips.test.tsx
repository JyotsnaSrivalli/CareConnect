import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Strip, Tablet } from '../src/types'

// ---- a tiny fake backend: it owns the truth (compartment states + stock), exactly like the real server ----
let strip: Strip
let release: (() => void) | null
const api = vi.hoisted(() => ({ getStrips: vi.fn(), takeTablet: vi.fn(), undoTablet: vi.fn() }))
vi.mock('../src/api/strips', () => api)

import BlisterStrips from '../src/components/BlisterStrips'

const TAKEN_AT = '2026-11-03T08:07:00'

function makeStrip(count: number): Strip {
  const tablets: Tablet[] = Array.from({ length: count }, (_, i) => ({ id: i + 1, slotNumber: i + 1, status: 'AVAILABLE', takenAt: null }))
  return {
    medicationId: 10, medicationName: 'Paracetamol', strength: '500 mg', type: 'TABLET', unit: 'TABLET', dosagePerDose: 1,
    totalTablets: count, takenTablets: 0, remainingTablets: count, lowStockThreshold: 3, lowStock: false,
    scheduleTimes: ['08:00:00', '20:00:00'], expectedToday: 2, takenToday: 0, tablets,
  }
}

function withTablet(id: number, taken: boolean): Strip {
  const tablets = strip.tablets.map((t) => (t.id === id ? { ...t, status: taken ? ('TAKEN' as const) : ('AVAILABLE' as const), takenAt: taken ? TAKEN_AT : null } : t))
  const delta = taken ? 1 : -1
  return { ...strip, tablets, takenTablets: strip.takenTablets + delta, remainingTablets: strip.remainingTablets - delta, takenToday: strip.takenToday + delta }
}

beforeEach(() => {
  strip = makeStrip(12)
  release = null
  api.getStrips.mockImplementation(async () => structuredClone([strip]))
  api.takeTablet.mockImplementation(async (id: number) => {
    await new Promise<void>((resolve) => { release = resolve })     // the server is "thinking"
    strip = withTablet(id, true)
    return strip
  })
  api.undoTablet.mockImplementation(async (id: number) => {
    strip = withTablet(id, false)
    return strip
  })
})
afterEach(() => { cleanup(); vi.clearAllMocks(); vi.restoreAllMocks() })

describe('tablet blister strip', () => {
  it('shows ONE strip with 12 individually clickable compartments and the remaining stock', async () => {
    render(<BlisterStrips editable />)

    expect(await screen.findAllByRole('button', { name: /^Tablet \d+, available/ })).toHaveLength(12)
    expect(screen.getAllByRole('article')).toHaveLength(1)
    expect(screen.getByText('0 of 12 taken')).toBeInTheDocument()
    expect(screen.getByText('One physical strip · 12 tablets')).toBeInTheDocument()
    expect(screen.getAllByText('Available')).toHaveLength(12)
    expect(screen.getByText('Remaining stock').parentElement).toHaveTextContent('12 tablets')
  })

  it('does not hardcode 12: the number of compartments comes from the server', async () => {
    strip = makeStrip(5)
    render(<BlisterStrips editable />)
    expect(await screen.findAllByRole('button', { name: /^Tablet \d+, available/ })).toHaveLength(5)
    expect(screen.getByText('0 of 5 taken')).toBeInTheDocument()
  })

  it('stays available until the backend confirms, then that compartment turns green with the time and stock drops to 11', async () => {
    const user = userEvent.setup()
    render(<BlisterStrips editable />)

    await user.click(await screen.findByRole('button', { name: /^Tablet 3, available/ }))
    expect(api.takeTablet).toHaveBeenCalledTimes(1)
    expect(api.takeTablet).toHaveBeenCalledWith(3)                  // exactly that compartment
    expect(screen.queryByText('Taken')).not.toBeInTheDocument()     // not shown as taken before the server answers
    expect(screen.getByText('Saving…')).toBeInTheDocument()

    release!()
    const taken = await screen.findByRole('button', { name: /^Tablet 3, taken on .*8:07/ })
    expect(taken).toHaveAttribute('aria-pressed', 'true')
    expect(within(taken).getByText('Taken')).toBeInTheDocument()
    expect(screen.getByText('1 of 12 taken')).toBeInTheDocument()
    expect(screen.getByText('Remaining stock').parentElement).toHaveTextContent('11 tablets')
    expect(screen.getByRole('button', { name: /^Tablet 1, available/ })).toBeInTheDocument()   // others untouched
  })

  it('morning and evening tablets are two different compartments on the same strip: 12 -> 11 -> 10', async () => {
    const user = userEvent.setup()
    render(<BlisterStrips editable />)

    await user.click(await screen.findByRole('button', { name: /^Tablet 1, available/ }))
    release!()
    await screen.findByRole('button', { name: /^Tablet 1, taken/ })
    expect(screen.getByText('Remaining stock').parentElement).toHaveTextContent('11 tablets')

    await user.click(screen.getByRole('button', { name: /^Tablet 2, available/ }))
    release!()
    await screen.findByRole('button', { name: /^Tablet 2, taken/ })

    expect(screen.getByText('Remaining stock').parentElement).toHaveTextContent('10 tablets')
    expect(screen.getByText('2 of 12 taken')).toBeInTheDocument()
    expect(screen.getAllByRole('article')).toHaveLength(1)           // never a second strip
    expect(screen.getByText(/Today:/)).toHaveTextContent('2 of 2 tablets taken')
  })

  it('blocks double clicks and clicks on other compartments while a request is running', async () => {
    const user = userEvent.setup()
    render(<BlisterStrips editable />)
    const first = await screen.findByRole('button', { name: /^Tablet 1, available/ })
    const second = screen.getByRole('button', { name: /^Tablet 2, available/ })

    await user.dblClick(first)
    await user.click(second)

    expect(api.takeTablet).toHaveBeenCalledTimes(1)                  // one request only
    expect(first).toBeDisabled()
    expect(second).toBeDisabled()
    release!()
    await waitFor(() => expect(screen.getByRole('button', { name: /^Tablet 2, available/ })).toBeEnabled())
    expect(strip.remainingTablets).toBe(11)                          // deducted once
  })

  it('undo asks first, then restores that exact compartment and one unit of stock after the server confirms', async () => {
    const user = userEvent.setup()
    render(<BlisterStrips editable />)
    await user.click(await screen.findByRole('button', { name: /^Tablet 3, available/ }))
    release!()
    const taken = await screen.findByRole('button', { name: /^Tablet 3, taken/ })

    vi.spyOn(window, 'confirm').mockReturnValue(false)
    await user.click(taken)
    expect(api.undoTablet).not.toHaveBeenCalled()                    // cancelled: nothing happens
    expect(screen.getByText('1 of 12 taken')).toBeInTheDocument()

    vi.spyOn(window, 'confirm').mockReturnValue(true)
    await user.click(taken)
    expect(api.undoTablet).toHaveBeenCalledWith(3)
    expect(await screen.findByRole('button', { name: /^Tablet 3, available/ })).toBeInTheDocument()
    expect(screen.getByText('0 of 12 taken')).toBeInTheDocument()
    expect(screen.getByText('Remaining stock').parentElement).toHaveTextContent('12 tablets')
  })

  it('keeps the compartment available and shows the server message when the backend refuses', async () => {
    const user = userEvent.setup()
    api.takeTablet.mockRejectedValueOnce({ isAxiosError: true, response: { status: 409, data: { message: 'This tablet is already marked as taken' } } })
    render(<BlisterStrips editable />)

    await user.click(await screen.findByRole('button', { name: /^Tablet 4, available/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent('already marked as taken')
    expect(screen.getAllByText('Available')).toHaveLength(12)
    expect(screen.queryByText('Taken')).not.toBeInTheDocument()
  })

  it('child view: same strip, read-only, no clickable compartments, loaded for the approved parent', async () => {
    render(<BlisterStrips editable={false} parentId={3} parentName="Asha" />)

    expect(await screen.findAllByRole('img', { name: /^Tablet \d+, available/ })).toHaveLength(12)
    expect(screen.queryByRole('button', { name: /Tablet/ })).not.toBeInTheDocument()
    expect(screen.queryByText(/Click a tablet|Undo/)).not.toBeInTheDocument()
    expect(api.getStrips).toHaveBeenCalledWith(3)
    expect(screen.getByText('Asha’s medicine strips')).toBeInTheDocument()
  })

  it('refreshing the browser shows the completed compartments again because the state is read from the server', async () => {
    const user = userEvent.setup()
    const first = render(<BlisterStrips editable />)
    await user.click(await screen.findByRole('button', { name: /^Tablet 2, available/ }))
    release!()
    await screen.findByRole('button', { name: /^Tablet 2, taken/ })
    first.unmount()                                                  // "refresh"

    render(<BlisterStrips editable />)
    expect(await screen.findByRole('button', { name: /^Tablet 2, taken/ })).toBeInTheDocument()
    expect(screen.getByText('1 of 12 taken')).toBeInTheDocument()
    expect(screen.getByText('Remaining stock').parentElement).toHaveTextContent('11 tablets')
  })

  it('renders nothing when there are no tablet or capsule medicines', async () => {
    api.getStrips.mockResolvedValue([])
    const { container } = render(<BlisterStrips editable />)
    await waitFor(() => expect(api.getStrips).toHaveBeenCalled())
    await waitFor(() => expect(container).toBeEmptyDOMElement())
  })
})

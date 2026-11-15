import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Checkin } from '../src/types'

const api = vi.hoisted(() => ({ saveCheckin: vi.fn(), getCheckins: vi.fn(), getCheckinForDate: vi.fn() }))
vi.mock('../src/api/checkins', () => api)

import CheckinHistory from '../src/components/CheckinHistory'
import ParentCheckins from '../src/components/ParentCheckins'
import { todayLocal } from '../src/utils/format'

function checkin(over: Partial<Checkin> = {}): Checkin {
  return {
    id: 1, date: '2026-11-10', feeling: 'GOOD', energyLevel: 'HIGH', symptoms: null, notes: null,
    createdAt: '2026-11-10T08:00:00', updatedAt: '2026-11-10T08:00:00', ...over,
  }
}

beforeEach(() => {
  api.getCheckins.mockResolvedValue([])
  api.getCheckinForDate.mockResolvedValue(null)
  api.saveCheckin.mockImplementation(async (p) => ({ checkin: checkin({ id: 5, ...p }), created: true }))
})
afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('check-in form (parent)', () => {
  it('saves a new check-in for the selected day and refreshes the history', async () => {
    const user = userEvent.setup()
    render(<ParentCheckins />)
    await screen.findByRole('button', { name: 'Save check-in' })
    await waitFor(() => expect(screen.getByRole('button', { name: 'Save check-in' })).toBeEnabled())

    await user.click(screen.getByRole('radio', { name: 'Good' }))
    await user.click(screen.getByRole('radio', { name: 'High' }))
    await user.type(screen.getByLabelText(/Symptoms or concerns/), '  mild cough ')
    await user.click(screen.getByRole('button', { name: 'Save check-in' }))

    expect(await screen.findByText(/Check-in saved for/)).toBeInTheDocument()
    expect(api.saveCheckin).toHaveBeenCalledTimes(1)
    expect(api.saveCheckin).toHaveBeenCalledWith({
      date: todayLocal(), feeling: 'GOOD', energyLevel: 'HIGH', symptoms: 'mild cough', notes: null,   // trimmed; empty notes -> null
    })
    // The history was read once on load and once more after the save
    await waitFor(() => expect(api.getCheckins).toHaveBeenCalledTimes(2))
    // and the button now says "Update", because that day has a check-in
    expect(screen.getByRole('button', { name: 'Update check-in' })).toBeInTheDocument()
  })

  it('does not call the server when the feeling or energy is missing', async () => {
    const user = userEvent.setup()
    render(<ParentCheckins />)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Save check-in' })).toBeEnabled())

    await user.click(screen.getByRole('radio', { name: 'Okay' }))      // energy still missing
    await user.click(screen.getByRole('button', { name: 'Save check-in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Please choose how you feel overall and your energy level.')
    expect(api.saveCheckin).not.toHaveBeenCalled()
  })

  it('pre-fills an existing check-in for the day and UPDATES it (no duplicate)', async () => {
    const user = userEvent.setup()
    api.getCheckinForDate.mockResolvedValue(checkin({ id: 7, date: todayLocal(), feeling: 'OKAY', energyLevel: 'LOW', symptoms: 'headache', notes: 'slept badly' }))
    api.saveCheckin.mockImplementation(async (p) => ({ checkin: checkin({ id: 7, ...p }), created: false }))
    render(<ParentCheckins />)

    expect(await screen.findByRole('button', { name: 'Update check-in' })).toBeInTheDocument()
    expect(screen.getByText(/You already checked in for/)).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'Okay' })).toBeChecked()
    expect(screen.getByRole('radio', { name: 'Low' })).toBeChecked()
    expect(screen.getByLabelText(/Symptoms or concerns/)).toHaveValue('headache')
    expect(screen.getByLabelText(/Notes/)).toHaveValue('slept badly')

    await user.click(screen.getByRole('radio', { name: 'Not good' }))
    await user.click(screen.getByRole('button', { name: 'Update check-in' }))

    expect(await screen.findByText(/updated\./)).toBeInTheDocument()
    expect(api.saveCheckin).toHaveBeenCalledTimes(1)
    expect(api.saveCheckin).toHaveBeenCalledWith({
      date: todayLocal(), feeling: 'NOT_GOOD', energyLevel: 'LOW', symptoms: 'headache', notes: 'slept badly',
    })
  })

  it('looks up the check-in of another day when the date changes, and clears the form if there is none', async () => {
    api.getCheckinForDate.mockImplementation(async (d: string) => (d === todayLocal() ? checkin({ date: d, feeling: 'GOOD', energyLevel: 'HIGH' }) : null))
    render(<ParentCheckins />)
    expect(await screen.findByRole('radio', { name: 'Good' })).toBeChecked()

    fireEvent.change(screen.getByLabelText(/^Day/), { target: { value: '2026-11-01' } })

    await waitFor(() => expect(api.getCheckinForDate).toHaveBeenLastCalledWith('2026-11-01'))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Save check-in' })).toBeEnabled())
    expect(screen.getByRole('radio', { name: 'Good' })).not.toBeChecked()
  })

  it('does not allow a date in the future (the date picker stops at today)', async () => {
    render(<ParentCheckins />)
    expect(await screen.findByLabelText(/^Day/)).toHaveAttribute('max', todayLocal())
  })

  it('shows the server message and keeps what was typed when saving fails', async () => {
    const user = userEvent.setup()
    api.saveCheckin.mockRejectedValue({ isAxiosError: true, response: { status: 400, data: { message: 'Date cannot be in the future' } } })
    render(<ParentCheckins />)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Save check-in' })).toBeEnabled())

    await user.click(screen.getByRole('radio', { name: 'Good' }))
    await user.click(screen.getByRole('radio', { name: 'High' }))
    await user.type(screen.getByLabelText(/Notes/), 'my note')
    await user.click(screen.getByRole('button', { name: 'Save check-in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Date cannot be in the future')
    expect(screen.getByLabelText(/Notes/)).toHaveValue('my note')
    expect(screen.queryByText(/Check-in saved/)).not.toBeInTheDocument()
  })

  it('tells the person when an old-style answer was saved for that day', async () => {
    api.getCheckinForDate.mockResolvedValue(checkin({ date: todayLocal(), feeling: 'FEELING_UNWELL', energyLevel: null }))
    render(<ParentCheckins />)
    expect(await screen.findByText(/older answer/)).toBeInTheDocument()
    expect(screen.queryAllByRole('radio', { checked: true })).toHaveLength(0)    // nothing pre-selected: they must choose
  })
})

describe('check-in history (parent)', () => {
  it('shows loading, then the entries newest first with feeling, energy, symptoms and notes', async () => {
    api.getCheckins.mockResolvedValue([
      checkin({ id: 2, date: '2026-11-12', feeling: 'NOT_GOOD', energyLevel: 'LOW', symptoms: 'headache', notes: 'rested' }),
      checkin({ id: 1, date: '2026-11-11', feeling: 'GOOD', energyLevel: 'HIGH' }),
    ])
    render(<ParentCheckins />)

    const list = await screen.findByRole('list')
    const items = within(list).getAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(items[0]).toHaveTextContent('Feeling: Not good')
    expect(items[0]).toHaveTextContent('Energy: Low')
    expect(items[0]).toHaveTextContent('Symptoms or concerns: headache')
    expect(items[0]).toHaveTextContent('Notes: rested')
    expect(items[1]).toHaveTextContent('Feeling: Good')
    expect(items[1]).not.toHaveTextContent('Notes:')
    expect(api.getCheckins).toHaveBeenCalledWith(undefined)
  })

  it('shows an empty state when there are no check-ins yet', async () => {
    render(<ParentCheckins />)
    expect(await screen.findByText(/No check-ins yet/)).toBeInTheDocument()
  })

  it('shows an error when the history cannot be loaded', async () => {
    api.getCheckins.mockRejectedValue({ isAxiosError: true, response: { status: 500, data: { message: 'Server error' } } })
    render(<ParentCheckins />)
    expect(await screen.findByText('Server error')).toBeInTheDocument()
  })

  it('shows old rows (old feeling value, no energy level) instead of crashing', async () => {
    api.getCheckins.mockResolvedValue([checkin({ feeling: 'FEELING_UNWELL', energyLevel: null })])
    render(<ParentCheckins />)
    expect(await screen.findByText('Feeling: Feeling unwell')).toBeInTheDocument()
    expect(screen.getByText('Energy: Not recorded')).toBeInTheDocument()
  })

  it('"Edit" loads that day into the form so it can be changed', async () => {
    const user = userEvent.setup()
    api.getCheckins.mockResolvedValue([checkin({ id: 2, date: '2026-11-10', feeling: 'OKAY', energyLevel: 'LOW' })])
    api.getCheckinForDate.mockImplementation(async (d: string) => (d === '2026-11-10' ? checkin({ id: 2, date: d, feeling: 'OKAY', energyLevel: 'LOW' }) : null))
    render(<ParentCheckins />)

    await user.click(await screen.findByRole('button', { name: /^Edit check-in for/ }))

    await waitFor(() => expect(api.getCheckinForDate).toHaveBeenLastCalledWith('2026-11-10'))
    expect(await screen.findByRole('button', { name: 'Update check-in' })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'Okay' })).toBeChecked()
    expect(screen.getByLabelText(/^Day/)).toHaveValue('2026-11-10')
  })
})

describe('check-in history (child, read-only)', () => {
  it('asks for the parent’s history by parent id and shows no form and no Edit buttons', async () => {
    api.getCheckins.mockResolvedValue([checkin({ symptoms: 'tired' })])
    render(<CheckinHistory parentId={3} parentName="Asha" />)

    expect(await screen.findByRole('heading', { name: 'Asha’s check-ins' })).toBeInTheDocument()
    expect(await screen.findByRole('listitem')).toHaveTextContent('Symptoms or concerns: tired')
    expect(api.getCheckins).toHaveBeenCalledWith(3)
    expect(screen.queryByRole('button', { name: /Edit|Save|Update/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('radio')).not.toBeInTheDocument()
  })

  it('shows a friendly message and does not even call the server when sharing is turned off', async () => {
    render(<CheckinHistory parentId={3} parentName="Asha" sharingDisabled />)
    expect(await screen.findByText(/has not turned on check-in sharing/)).toBeInTheDocument()
    expect(api.getCheckins).not.toHaveBeenCalled()
  })

  it('shows the same friendly message (not an error) when the server answers 403', async () => {
    api.getCheckins.mockRejectedValue({ isAxiosError: true, response: { status: 403, data: { message: 'This parent has not shared their check-ins with you' } } })
    render(<CheckinHistory parentId={3} parentName="Asha" />)
    expect(await screen.findByText(/has not turned on check-in sharing/)).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('shows an empty state when the parent has no check-ins yet', async () => {
    render(<CheckinHistory parentId={3} parentName="Asha" />)
    expect(await screen.findByText('No check-ins have been recorded yet.')).toBeInTheDocument()
  })
})

import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const auth = vi.hoisted(() => ({ login: vi.fn(), register: vi.fn() }))
vi.mock('../src/context/AuthContext', () => ({ useAuth: () => auth }))

import LoginPage from '../src/pages/LoginPage'
import RegisterPage from '../src/pages/RegisterPage'

beforeEach(() => {
  auth.login.mockResolvedValue({ id: 1, name: 'Asha', email: 'a@t.com', role: 'PARENT' })
  auth.register.mockResolvedValue({ id: 1, name: 'Asha', email: 'a@t.com', role: 'PARENT' })
})
afterEach(() => { cleanup(); vi.clearAllMocks() })

const renderLogin = () => render(<MemoryRouter><LoginPage /></MemoryRouter>)
const renderRegister = () => render(<MemoryRouter><RegisterPage /></MemoryRouter>)

describe('password visibility toggle', () => {
  it('login: starts hidden, shows the password when clicked, hides it again, and never clears what was typed', async () => {
    const user = userEvent.setup()
    renderLogin()
    const input = screen.getByLabelText('Password')
    await user.type(input, 'Secret123')

    expect(input).toHaveAttribute('type', 'password')
    const show = screen.getByRole('button', { name: 'Show password' })
    expect(show).toHaveAttribute('aria-pressed', 'false')

    await user.click(show)
    expect(input).toHaveAttribute('type', 'text')
    expect(input).toHaveValue('Secret123')                              // value kept
    const hide = screen.getByRole('button', { name: 'Hide password' })
    expect(hide).toHaveAttribute('aria-pressed', 'true')

    await user.click(hide)
    expect(input).toHaveAttribute('type', 'password')
    expect(input).toHaveValue('Secret123')
  })

  it('login: the toggle does not submit the form, and the same password is sent to login() as before', async () => {
    const user = userEvent.setup()
    renderLogin()
    await user.type(screen.getByLabelText('Email'), 'a@t.com')
    await user.type(screen.getByLabelText('Password'), 'Secret123')

    await user.click(screen.getByRole('button', { name: 'Show password' }))
    expect(auth.login).not.toHaveBeenCalled()                           // clicking the eye is not a submit

    await user.click(screen.getByRole('button', { name: 'Log in' }))
    expect(auth.login).toHaveBeenCalledWith('a@t.com', 'Secret123')
  })

  it('login: the eye button can be used with the keyboard', async () => {
    const user = userEvent.setup()
    renderLogin()
    await user.type(screen.getByLabelText('Password'), 'Secret123')
    await user.tab()                                                    // next focusable element after the input = the eye button
    expect(screen.getByRole('button', { name: 'Show password' })).toHaveFocus()
    await user.keyboard('{Enter}')
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'text')
  })

  it('register: has the same toggle, keeps the hint and the 8-character rule, and still registers with the typed password', async () => {
    const user = userEvent.setup()
    renderRegister()
    const input = screen.getByLabelText(/^Password/)
    expect(input).toHaveAttribute('minlength', '8')
    expect(input).toHaveAttribute('autocomplete', 'new-password')
    expect(screen.getByText('(at least 8 characters)')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Full name'), 'Asha')
    await user.type(screen.getByLabelText('Email'), 'a@t.com')
    await user.type(input, 'Secret123')
    await user.click(screen.getByRole('button', { name: 'Show password' }))
    expect(input).toHaveAttribute('type', 'text')
    expect(input).toHaveValue('Secret123')

    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(auth.register).toHaveBeenCalledWith(expect.objectContaining({ name: 'Asha', email: 'a@t.com', password: 'Secret123' }))
  })
})

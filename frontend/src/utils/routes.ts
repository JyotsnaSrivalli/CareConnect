import type { Role } from '../types'

export const homePathFor = (role: Role): string => (role === 'PARENT' ? '/parent' : '/child')

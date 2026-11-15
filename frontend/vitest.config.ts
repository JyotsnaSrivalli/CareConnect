import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// Optional component tests. Not part of `npm run build` (tests live outside src/).
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    include: ['tests/**/*.test.tsx'],
    setupFiles: ['tests/setup.ts'],
  },
})

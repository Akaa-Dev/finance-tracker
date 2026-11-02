import { defineConfig, devices } from '@playwright/test'
export default defineConfig({
  testDir: './e2e', fullyParallel: false, workers: 1, timeout: 45000,
  reporter: [['list']],
  use: { baseURL: 'http://127.0.0.1:15173', trace: 'off' },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile-360', use: { viewport: { width: 360, height: 800 } } },
  ],
  webServer: { command: 'npm run dev -- --port 15173', url: 'http://127.0.0.1:15173', reuseExistingServer: false },
})

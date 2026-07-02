import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: [['html'], ['list']],
  use: {
    baseURL: 'http://localhost:3010',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    // Playwright 自动启动 dev server，端口由 vite.config.ts 决定（3010）
    command: 'npm run dev',
    url: 'http://localhost:3010',
    reuseExistingServer: true,
    timeout: 120 * 1000,
  },
  timeout: 60 * 1000,
});

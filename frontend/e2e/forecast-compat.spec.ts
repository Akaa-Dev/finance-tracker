import { test, expect } from '@playwright/test'

test('older forecast response shows a restart message without blanking the app', async ({ page }) => {
  await page.goto('/')
  await page.getByLabel('Email', { exact: true }).fill(process.env.OWNER_EMAIL!)
  await page.getByLabel('Password', { exact: true }).fill(process.env.OWNER_PASSWORD!)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('navigation', { name: 'Main navigation' })).toBeVisible()
  await page.route('**/api/v1/forecasts/cashflow?**', async route => {
    const response = await route.fetch()
    const old = await response.json()
    delete old.accountBalances
    delete old.startingDebt
    delete old.endingDebt
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(old) })
  })
  await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: 'Forecast' }).click()
  await expect(page.getByRole('alert')).toContainText('Restart the backend, then retry.')
  await expect(page.getByRole('heading', { name: 'Forecast', exact: true })).toBeVisible()
  await expect(page.getByRole('navigation', { name: 'Main navigation' })).toBeVisible()
})

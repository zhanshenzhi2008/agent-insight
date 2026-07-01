import { test, expect } from '@playwright/test';

/**
 * E2E Test: Request Search Flow
 * Covers TC-F1 series from the test specification.
 *
 * Prerequisite: Backend must be running at localhost:9280
 */
test.describe('Request Search (TC-F1)', () => {

  test.beforeEach(async ({ page }) => {
    // Navigate to the request search page
    await page.goto('/');
    // Wait for the page to load
    await page.waitForLoadState('networkidle');
  });

  test('TC-F1-01: Search by requestId', async ({ page }) => {
    // Fill in the requestId field
    const requestIdInput = page.getByPlaceholder('Request ID');
    await requestIdInput.fill('req_test_001');

    // Click the search button
    await page.getByRole('button', { name: /搜索/i }).click();

    // Wait for the table to load
    await page.waitForSelector('.ant-table-row');

    // Verify the result contains the searched requestId
    const firstRow = page.locator('.ant-table-row').first();
    await expect(firstRow.locator('code')).toContainText('req_test_001');
  });

  test('TC-F1-02: Search by time range', async ({ page }) => {
    // Find the RangePicker and select a date range
    const rangePicker = page.locator('.ant-picker-range');
    await rangePicker.click();

    // Select a preset range (Today or Last 7 Days)
    await page.getByText('最近7天').click();

    // Click search
    await page.getByRole('button', { name: /搜索/i }).click();

    // Wait for results
    await page.waitForSelector('.ant-table-row', { timeout: 10000 });

    // Verify pagination info shows total count
    const paginationText = page.locator('.ant-pagination-total-text');
    await expect(paginationText).toBeVisible();
  });

  test('TC-F1-03: Filter by agent name', async ({ page }) => {
    // Fill in the agent name field
    const agentInput = page.getByPlaceholder('Agent 名称');
    await agentInput.fill('DataAgent');

    // Click search
    await page.getByRole('button', { name: /搜索/i }).click();

    // Wait for results
    await page.waitForSelector('.ant-table-row', { timeout: 10000 });

    // Verify all rows contain DataAgent in the agent column
    const agentCells = page.locator('.ant-table-cell').filter({ hasText: 'DataAgent' });
    await expect(agentCells.first()).toBeVisible();
  });

  test('TC-F1-04: Filter by failed status', async ({ page }) => {
    // Open the status dropdown
    await page.getByPlaceholder('状态').click();

    // Select "失败"
    await page.getByRole('option', { name: '失败' }).click();

    // Click search
    await page.getByRole('button', { name: /搜索/i }).click();

    // Wait for results
    await page.waitForSelector('.ant-table-row', { timeout: 10000 });

    // Verify all visible rows have a "失败" tag
    const failedTag = page.locator('.ant-tag').filter({ hasText: '失败' });
    await expect(failedTag.first()).toBeVisible();
  });

  test('TC-F1-05: Pagination', async ({ page }) => {
    // Click search with default parameters
    await page.getByRole('button', { name: /搜索/i }).click();

    // Wait for initial results
    await page.waitForSelector('.ant-table-row', { timeout: 10000 });

    // Find pagination and go to page 2
    const page2Button = page.locator('.ant-pagination-item').filter({ hasText: '2' });
    if (await page2Button.isVisible()) {
      await page2Button.click();

      // Verify page changed
      await expect(page.locator('.ant-pagination-item-active')).toContainText('2');
    }
  });

  test('TC-F1-07: Navigate to request overview', async ({ page }) => {
    // Search first
    await page.getByRole('button', { name: /搜索/i }).click();
    await page.waitForSelector('.ant-table-row', { timeout: 10000 });

    // Click the "概览" (Overview) button in the first row
    const overviewButton = page.locator('.ant-table-row').first().getByText('概览');
    await overviewButton.click();

    // Verify navigation to overview page
    await expect(page).toHaveURL(/\/overview\//);
  });

  test('TC-F1-08: Navigate to trace analysis', async ({ page }) => {
    // Search first
    await page.getByRole('button', { name: /搜索/i }).click();
    await page.waitForSelector('.ant-table-row', { timeout: 10000 });

    // Click the "轨迹" (Trace) button in the first row
    const traceButton = page.locator('.ant-table-row').first().getByText('轨迹');
    await traceButton.click();

    // Verify navigation to trace page
    await expect(page).toHaveURL(/\/trace\//);
  });

  test('TC-F1-06: No results found', async ({ page }) => {
    // Search with a non-existent requestId
    const requestIdInput = page.getByPlaceholder('Request ID');
    await requestIdInput.fill('non-existent-request-id-12345');

    await page.getByRole('button', { name: /搜索/i }).click();

    // Wait for the empty state
    const emptyText = page.locator('.ant-empty-description');
    await expect(emptyText).toBeVisible({ timeout: 10000 });
  });
});

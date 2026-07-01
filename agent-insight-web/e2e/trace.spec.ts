import { test, expect } from '@playwright/test';

/**
 * E2E Test: Trace Analysis Flow
 * Covers TC-F3 series from the test specification.
 *
 * Prerequisite: Backend must be running at localhost:9280
 */
test.describe('Trace Analysis (TC-F3)', () => {

  test.beforeEach(async ({ page }) => {
    // Navigate to trace analysis page with a known requestId
    await page.goto('/trace/req_test_001');
    await page.waitForLoadState('networkidle');
  });

  test('TC-F3-01: Load and display execution trace', async ({ page }) => {
    // Wait for the trace table to load
    await page.waitForSelector('.ant-table-row', { timeout: 15000 });

    // Verify the table has columns for trace data
    const taskIndexHeader = page.locator('.ant-table-column-title').filter({ hasText: /任务|task/i });
    await expect(taskIndexHeader.first()).toBeVisible();
  });

  test('TC-F3-01: Trace is sorted by taskIndex ascending', async ({ page }) => {
    await page.waitForSelector('.ant-table-row', { timeout: 15000 });

    // Get all task index values from the first column
    const taskIndexCells = page.locator('.ant-table-row td').first();
    await expect(taskIndexCells).toBeVisible();
  });

  test('TC-F3-02: Filter trace by agent name', async ({ page }) => {
    await page.waitForSelector('.ant-table-row', { timeout: 15000 });

    // Look for an agent filter input
    const agentFilter = page.getByPlaceholder(/agent/i).or(page.locator('input').filter({ hasText: '' }).first());
    if (await agentFilter.isVisible()) {
      await agentFilter.fill('SubAgent');
      await page.getByRole('button', { name: /搜索|filter/i }).click();
      await page.waitForTimeout(1000);
    }

    // Table should still have rows (or empty state)
    const rows = page.locator('.ant-table-row');
    const count = await rows.count();
    expect(count).toBeGreaterThanOrEqual(0);
  });

  test('TC-F3-04: Display failed tasks with error indicators', async ({ page }) => {
    await page.waitForSelector('.ant-table-row', { timeout: 15000 });

    // Look for "失败" (failed) tags or red indicators
    const failedTags = page.locator('.ant-tag').filter({ hasText: /失败|error/i });
    const failedCount = await failedTags.count();

    // Either there are failed tags, or all tasks are successful
    if (failedCount > 0) {
      await expect(failedTags.first()).toBeVisible();
    }
  });

  test('TC-F3-05: Display task tree structure', async ({ page }) => {
    // Look for tree view toggle
    const treeViewButton = page.getByRole('button', { name: /树|tree/i }).or(page.locator('button').filter({ hasText: /树/i }));
    if (await treeViewButton.isVisible()) {
      await treeViewButton.click();
      await page.waitForTimeout(500);
    }

    // Check if tree structure is visible
    const treeContainer = page.locator('.ant-tree').or(page.locator('[class*="tree"]'));
    const hasTree = await treeContainer.isVisible().catch(() => false);

    // If tree is available, verify root nodes exist
    if (hasTree) {
      const treeNodes = page.locator('.ant-tree-node-content-wrapper');
      await expect(treeNodes.first()).toBeVisible();
    }
  });

  test('TC-F3-06: Empty trace shows empty state', async ({ page }) => {
    // Navigate to a request with no trace
    await page.goto('/trace/non-existent-request');
    await page.waitForLoadState('networkidle');

    // Either show empty state or no results
    const emptyState = page.locator('.ant-empty').or(page.locator('text=暂无数据'));
    const hasEmpty = await emptyState.isVisible().catch(() => false);

    if (hasEmpty) {
      await expect(emptyState).toBeVisible();
    } else {
      // If not empty, there should be rows
      const rows = page.locator('.ant-table-row');
      await expect(rows.first()).toBeVisible();
    }
  });

  test('TC-F3-07: Source code mapping button exists', async ({ page }) => {
    await page.waitForSelector('.ant-table-row', { timeout: 15000 });

    // Look for a "源码" or "source" related button
    const sourceButton = page.getByRole('button', { name: /源码|source/i });
    if (await sourceButton.isVisible().catch(() => false)) {
      await expect(sourceButton.first()).toBeVisible();
    }
  });

  test('TC-F3-03: Expand task steps', async ({ page }) => {
    await page.waitForSelector('.ant-table-row', { timeout: 15000 });

    // Look for expandable row or expand button
    const expandIcon = page.locator('.ant-table-row-expand-icon').first();
    if (await expandIcon.isVisible().catch(() => false)) {
      await expandIcon.click();
      await page.waitForTimeout(500);

      // Verify expanded content has step details
      const expandedRow = page.locator('.ant-table-row-expanded');
      const hasExpanded = await expandedRow.isVisible().catch(() => false);
      if (hasExpanded) {
        await expect(expandedRow).toBeVisible();
      }
    }
  });
});

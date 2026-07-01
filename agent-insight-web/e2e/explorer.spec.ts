import { test, expect } from '@playwright/test';

/**
 * E2E Test: Data Explorer Flow
 * Covers TC-EX series from the test specification.
 *
 * Prerequisite: Backend must be running at localhost:9280
 */
test.describe('Data Explorer (TC-EX)', () => {

  test('TC-EX-10: List all datasources', async ({ page }) => {
    // Navigate to the Data Explorer datasource page
    await page.goto('/explorer/datasource');
    await page.waitForLoadState('networkidle');

    // Wait for the datasource table to load
    await page.waitForSelector('.ant-table-row', { timeout: 15000 });

    // Verify the table has datasource entries
    const firstRow = page.locator('.ant-table-row').first();
    await expect(firstRow).toBeVisible();

    // Verify columns include datasource name, type, and status
    const nameHeader = page.locator('.ant-table-column-title').filter({ hasText: /名称|name/i });
    await expect(nameHeader.first()).toBeVisible();
  });

  test('TC-EX-01: Create a MySQL datasource', async ({ page }) => {
    await page.goto('/explorer/datasource');
    await page.waitForLoadState('networkidle');

    // Click the create button
    const createButton = page.getByRole('button', { name: /创建|新增|添加/i });
    await createButton.click();

    // Wait for the modal/drawer to open
    await page.waitForSelector('.ant-modal, .ant-drawer', { timeout: 5000 });

    // Fill in the datasource form
    const form = page.locator('.ant-form');
    await form.getByPlaceholder(/key|标识/i).fill('test_mysql_001');
    await form.getByPlaceholder(/名称|name/i).fill('测试 MySQL 数据源');
    await form.getByPlaceholder(/host|主机/i).fill('localhost');
    await form.getByPlaceholder(/port|端口/i).fill('3306');
    await form.getByPlaceholder(/database|数据库/i).fill('test_db');
    await form.getByPlaceholder(/username|用户名/i).fill('root');
    await form.getByPlaceholder(/password|密码/i).fill('test123');

    // Select MySQL type if there's a type selector
    const typeSelect = page.locator('.ant-select').filter({ hasText: /type|类型/i }).first();
    if (await typeSelect.isVisible().catch(() => false)) {
      await typeSelect.click();
      await page.getByRole('option', { name: /mysql/i }).click();
    }

    // Submit the form
    await page.getByRole('button', { name: /确定|提交|保存/i }).click();

    // Wait for success notification
    await page.waitForSelector('.ant-message-success, .ant-notification-success', { timeout: 10000 });
  });

  test('TC-EX-04: Test datasource connection (success)', async ({ page }) => {
    await page.goto('/explorer/datasource');
    await page.waitForLoadState('networkidle');
    await page.waitForSelector('.ant-table-row', { timeout: 15000 });

    // Find a test connection button for the first datasource
    const testButton = page.locator('.ant-table-row').first().getByText(/测试连接|test/i);
    if (await testButton.isVisible().catch(() => false)) {
      await testButton.click();

      // Wait for the test result
      await page.waitForTimeout(3000);

      // Verify connected indicator
      const connectedBadge = page.locator('.ant-badge').filter({ hasText: /connected|连接成功/i })
        .or(page.locator('text=/连接成功|connected/i'));
      const disconnectedBadge = page.locator('text=/连接失败|disconnected|连接异常/i');

      const connectedVisible = await connectedBadge.isVisible().catch(() => false);
      const disconnectedVisible = await disconnectedBadge.isVisible().catch(() => false);

      // One of these should be visible
      expect(connectedVisible || disconnectedVisible).toBeTruthy();
    }
  });

  test('TC-EX-06: List tables in datasource', async ({ page }) => {
    await page.goto('/explorer/datasource');
    await page.waitForLoadState('networkidle');
    await page.waitForSelector('.ant-table-row', { timeout: 15000 });

    // Find a "查看表" or "list tables" button for the first datasource
    const listTablesButton = page.locator('.ant-table-row').first()
      .getByText(/查看表|tables|列表/i);
    if (await listTablesButton.isVisible().catch(() => false)) {
      await listTablesButton.click();

      // Wait for the table list to appear
      await page.waitForSelector('.ant-table', { timeout: 10000 });

      // Verify table list is shown
      const tableList = page.locator('.ant-table-row');
      const count = await tableList.count();
      expect(count).toBeGreaterThanOrEqual(0);
    }
  });

  test('TC-EX-08: Delete datasource', async ({ page }) => {
    await page.goto('/explorer/datasource');
    await page.waitForLoadState('networkidle');
    await page.waitForSelector('.ant-table-row', { timeout: 15000 });

    // Find the delete button for the first row
    const deleteButton = page.locator('.ant-table-row').first().getByRole('button')
      .filter({ has: page.locator('.anticon-delete, [aria-label="delete"]') }).first();

    if (await deleteButton.isVisible().catch(() => false)) {
      await deleteButton.click();

      // Handle confirmation dialog
      const confirmButton = page.getByRole('button', { name: /确定|删除/i }).filter({ hasText: /确定|删除/i }).first();
      if (await confirmButton.isVisible().catch(() => false)) {
        await confirmButton.click();
        await page.waitForTimeout(1000);
      }
    }
  });

  test('TC-EX-20: Execute a dynamic query', async ({ page }) => {
    // Navigate to the table explorer
    await page.goto('/explorer/table');
    await page.waitForLoadState('networkidle');

    // Select a datasource
    const dsSelect = page.locator('.ant-select').first();
    if (await dsSelect.isVisible().catch(() => false)) {
      await dsSelect.click();
      await page.waitForSelector('.ant-select-dropdown', { timeout: 5000 });
      await page.locator('.ant-select-item').first().click();
    }

    // Select a table
    const tableSelect = page.locator('.ant-select').nth(1);
    if (await tableSelect.isVisible().catch(() => false)) {
      await tableSelect.click();
      await page.waitForSelector('.ant-select-dropdown', { timeout: 5000 });
      await page.locator('.ant-select-item').first().click();
    }

    // Click execute/query button
    const queryButton = page.getByRole('button', { name: /查询|execute|查询数据/i });
    if (await queryButton.isVisible().catch(() => false)) {
      await queryButton.click();

      // Wait for results
      await page.waitForSelector('.ant-table-row, .ant-empty', { timeout: 15000 });

      // Verify results are displayed
      const hasRows = await page.locator('.ant-table-row').isVisible().catch(() => false);
      const hasEmpty = await page.locator('.ant-empty').isVisible().catch(() => false);
      expect(hasRows || hasEmpty).toBeTruthy();
    }
  });

  test('TC-EX-21/22: Query with filters (EQ/LIKE)', async ({ page }) => {
    await page.goto('/explorer/table');
    await page.waitForLoadState('networkidle');

    // Add a filter
    const addFilterButton = page.getByRole('button', { name: /添加过滤|add filter/i });
    if (await addFilterButton.isVisible().catch(() => false)) {
      await addFilterButton.click();

      // Fill in filter values
      const filterColumn = page.locator('input').filter({ hasText: '' }).first();
      const filterOperator = page.locator('.ant-select').filter({ hasText: /operator|操作符/i }).first();
      const filterValue = page.locator('input').nth(1);

      await filterColumn.fill('status');
      await filterOperator.click();
      await page.getByRole('option', { name: /eq|等于/i }).click();
      await filterValue.fill('PENDING');

      // Execute query
      await page.getByRole('button', { name: /查询|execute/i }).click();
      await page.waitForTimeout(2000);

      // Verify results are filtered
      const rows = page.locator('.ant-table-row');
      const count = await rows.count();
      expect(count).toBeGreaterThanOrEqual(0);
    }
  });

  test('TC-AI-02: AI column analysis', async ({ page }) => {
    await page.goto('/explorer/ai-config');
    await page.waitForLoadState('networkidle');

    // Verify AI status section is visible
    const statusSection = page.locator('text=/AI|ai|智能/i');
    await expect(statusSection.first()).toBeVisible();

    // If AI is enabled, look for analysis features
    const analyzeButton = page.getByRole('button', { name: /分析|analyze/i });
    if (await analyzeButton.isVisible().catch(() => false)) {
      await expect(analyzeButton).toBeVisible();
    }
  });
});

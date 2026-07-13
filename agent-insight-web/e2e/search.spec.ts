import { test, expect } from '@playwright/test';

/**
 * E2E Test: Request Search Flow
 * 覆盖 TC-F1 系列
 *
 * 前置条件：后端运行在 localhost:9280，前端 dev server 在 localhost:9281
 * 使用 Mock 响应，不依赖真实数据库
 */
test.describe('请求检索 (TC-F1)', () => {

  test.beforeEach(async ({ page }) => {
    // Mock API responses — 不依赖真实数据库
    await page.route('**/api/v1/requests**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: {
            content: [
              {
                requestId: 'req-test-001',
                topAgentName: 'DataAgent',
                agentId: 1,
                taskStatus: 2,
                success: true,
                totalTaskCount: 5,
                failedTaskCount: 0,
                totalDuration: 120000,
                createTime: '2025-01-15T10:00:00',
                subAgentNames: ['SubAgentA'],
              },
            ],
            totalElements: 1,
            totalPages: 1,
            page: 0,
            size: 20,
            hasNext: false,
          },
        }),
      });
    });
    await page.goto('/search');
    await page.waitForSelector('.ant-spin', { state: 'hidden', timeout: 10000 }).catch(() => {});
  });

  test('页面加载成功，请求检索标题可见', async ({ page }) => {
    await expect(page.locator('.ant-card-head-title').filter({ hasText: '请求检索' })).toBeVisible();
  });

  test('搜索按钮和表单字段存在', async ({ page }) => {
    const selects = page.locator('.ant-select');
    await expect(selects.nth(1)).toBeVisible();
    await expect(selects.nth(2)).toBeVisible();
    await expect(page.getByRole('button', { name: '搜索' })).toBeVisible();
  });

  test('点击搜索，表格显示数据行', async ({ page }) => {
    await page.getByRole('button', { name: '搜索' }).click();
    await page.waitForSelector('.ant-spin', { state: 'hidden', timeout: 8000 }).catch(() => {});
    await expect(page.locator('.ant-table-row')).toBeVisible({ timeout: 5000 });
  });

  test('表格显示正确的 requestId 数据', async ({ page }) => {
    await page.waitForSelector('.ant-table-row', { timeout: 5000 });
    await expect(page.locator('code').filter({ hasText: 'req-test-001' })).toBeVisible();
    await expect(page.locator('td').filter({ hasText: 'DataAgent' }).first()).toBeVisible();
  });

  test('操作列包含概览/轨迹/日志/LLM 链接按钮', async ({ page }) => {
    await page.waitForSelector('.ant-table-row', { timeout: 5000 });
    // antd v6 Button type="link" 渲染为 <a role="button">
    await expect(page.getByRole('button', { name: '概览' })).toBeVisible();
    await expect(page.getByRole('button', { name: '轨迹' })).toBeVisible();
    await expect(page.getByRole('button', { name: '日志' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'LLM' })).toBeVisible();
  });

  test('点击概览按钮，导航到概览页', async ({ page }) => {
    await page.waitForSelector('.ant-table-row', { timeout: 5000 });
    await page.getByRole('button', { name: '概览' }).click();
    await expect(page).toHaveURL(/\/overview\/req-test-001/);
  });

  test('点击轨迹按钮，导航到轨迹页', async ({ page }) => {
    await page.waitForSelector('.ant-table-row', { timeout: 5000 });
    await page.getByRole('button', { name: '轨迹' }).click();
    await expect(page).toHaveURL(/\/trace\/req-test-001/);
  });

});

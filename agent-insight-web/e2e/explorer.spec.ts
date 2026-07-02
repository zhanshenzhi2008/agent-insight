import { test, expect } from '@playwright/test';

/**
 * E2E Test: Data Explorer Flow
 * 覆盖 TC-EX 系列，重点验证 explorerApi baseURL 修复（explorerHttp/v1/explorer → api/v1/explorer）
 *
 * 前置条件：后端运行在 localhost:9280，MongoDB 可用
 */
test.describe('数据浏览器 (TC-EX)', () => {

  test('TC-EX-10: 数据源列表页加载成功（验证 explorerApi baseURL 修复）', async ({ page }) => {
    // 这是本次修复的核心测试：修复前 /explorerHttp/v1/explorer/datasources 404
    // 修复后 /api/v1/explorer/datasources 返回 200
    await page.goto('/explorer/datasource');
    await page.waitForLoadState('networkidle');

    // 页面标题存在
    await expect(page.getByText('数据源配置')).toBeVisible();

    // 等待表格加载（loading spinner 消失）
    await page.waitForSelector('.ant-spin', { state: 'hidden', timeout: 10000 }).catch(() => {});

    // 检查网络请求，确认不再 404
    const failedRequests: string[] = [];
    page.on('response', async (response) => {
      if (response.status() === 404 && response.url().includes('/explorer')) {
        failedRequests.push(response.url());
      }
    });

    // 刷新页面再次确认
    await page.reload();
    await page.waitForLoadState('networkidle');

    // 不应该有 /explorerHttp 相关的 404
    for (const url of failedRequests) {
      expect(url).not.toContain('/explorerHttp');
    }
  });

  test('TC-EX-01: 新建数据源按钮可见', async ({ page }) => {
    await page.goto('/explorer/datasource');
    await page.waitForLoadState('networkidle');
    await page.waitForSelector('.ant-spin', { state: 'hidden', timeout: 10000 }).catch(() => {});
    const createBtn = page.getByRole('button', { name: /新建数据源/i });
    await expect(createBtn).toBeVisible();
  });

  test('点击新建数据源，打开 Modal', async ({ page }) => {
    // Mock datasource list so page loads quickly
    await page.route('**/api/v1/explorer/datasources', async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, data: [] }) });
    });
    await page.goto('/explorer/datasource');
    await page.waitForLoadState('networkidle');
    await page.waitForSelector('.ant-spin', { state: 'hidden', timeout: 8000 }).catch(() => {});
    await page.getByRole('button', { name: /新建数据源/i }).click();
    const modal = page.locator('.ant-modal');
    await expect(modal).toBeVisible({ timeout: 5000 });
  });

  test('API 404 错误检测：刷新页面无 /explorerHttp 404', async ({ page }) => {
    const explorerHttp404s: string[] = [];
    page.on('response', (response) => {
      if (response.status() === 404 && response.url().includes('explorerHttp')) {
        explorerHttp404s.push(response.url());
      }
    });

    await page.goto('/explorer/datasource');
    await page.waitForLoadState('networkidle');
    await page.reload();
    await page.waitForLoadState('networkidle');

    // 修复前: explorerHttp/v1/explorer/datasources → 404
    // 修复后: /api/v1/explorer/datasources → 200
    expect(explorerHttp404s).toHaveLength(0);
  });

  test('侧边栏菜单"数据浏览器"可见', async ({ page }) => {
    await page.goto('/search');
    await page.waitForLoadState('networkidle');
    await expect(page.getByText('数据浏览器')).toBeVisible();
  });

});

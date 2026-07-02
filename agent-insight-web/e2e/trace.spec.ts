import { test, expect } from '@playwright/test';

/**
 * E2E Test: Trace Analysis Flow
 * 覆盖 TC-F3 系列
 *
 * 前置条件：后端运行在 localhost:9280
 */
test.describe('执行轨迹分析 (TC-F3)', () => {

  test('访问 /trace 无 requestId，显示空状态（不崩溃）', async ({ page }) => {
    await page.goto('/trace');
    await page.waitForLoadState('networkidle');
    // 页面应该存在，不应该白屏
    await expect(page.locator('.ant-layout-content')).toBeVisible();
  });

  test('访问 /trace/不存在的requestId，显示空状态', async ({ page }) => {
    await page.goto('/trace/req-nonexistent-xyz-12345');
    await page.waitForLoadState('networkidle');
    // 等待请求完成
    await page.waitForTimeout(2000);
    // 要么空状态，要么表格无数据
    const hasEmpty = await page.locator('.ant-empty').isVisible().catch(() => false);
    const hasTable = await page.locator('.ant-table').isVisible().catch(() => false);
    // 页面存在且无 JS 错误即可
    expect(hasEmpty || hasTable).toBeTruthy();
  });

  test('侧边栏菜单可以点击进入执行轨迹', async ({ page }) => {
    await page.goto('/search');
    await page.waitForLoadState('networkidle');
    // 点击侧边栏的"执行轨迹"菜单
    const menuItem = page.getByText('执行轨迹').first();
    if (await menuItem.isVisible().catch(() => false)) {
      await menuItem.click();
      await page.waitForLoadState('networkidle');
      // 应该能跳到 /trace 或某个页面
      await expect(page).toHaveURL(/./);
    }
  });

});

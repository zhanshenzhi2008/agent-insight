import { test, expect } from '@playwright/test';

/**
 * E2E Test: Datasource & TableConfig — covers the regressions reported on 2026-07-06:
 *   - 测试连接 / 从数据库导入表 按钮点击后用户能感知到结果
 *
 * 前置条件：后端运行在 localhost:9280，MongoDB 已初始化 log_llm_* collection，
 *           一个名为 "local-llm_agent" 的 MONGODB 数据源已配置。
 */
test.describe('数据源与表配置 — 反馈感知', () => {

  test('TC-EX-11: 数据源 "测试" 按钮点击后弹出 Modal/Tag 反馈', async ({ page }) => {
    // 用真实后端：连接 MongoDB 是已知的可达路径
    await page.goto('/explorer/datasource');
    await page.waitForLoadState('networkidle');
    await page.waitForSelector('.ant-spin', { state: 'hidden', timeout: 10000 }).catch(() => {});

    // 等到表格第一行加载
    const firstRow = page.locator('.ant-table-tbody tr').first();
    await expect(firstRow).toBeVisible({ timeout: 10000 });

    // 点击第一行的"测试"按钮
    const testBtn = firstRow.getByRole('button', { name: /测试/i });
    await testBtn.click();

    // 修复前的 bug：toast 一闪而过，用户无感知。
    // 修复后的契约：弹出 antd Modal.success 或 Modal.error，或 footer 出现 status Tag
    // 等待任一可见反馈（Modal 标题或 Tag）
    const feedback = page.locator('.ant-modal-confirm-title, .ant-modal-confirm-content, footer .ant-tag');
    await expect(feedback.first()).toBeVisible({ timeout: 10000 });

    // 关闭弹窗（如果存在）
    const okBtn = page.getByRole('button', { name: /知道了/ });
    if (await okBtn.isVisible().catch(() => false)) {
      await okBtn.click();
    }
  });

  test('TC-EX-12: 表配置页 — 选择数据源后 "从数据库导入表" 按钮可点击并有反馈', async ({ page }) => {
    await page.goto('/explorer/table');
    await page.waitForLoadState('networkidle');
    await page.waitForSelector('.ant-spin', { state: 'hidden', timeout: 10000 }).catch(() => {});

    // 等待 Select 数据源加载并选第一个
    const dsSelect = page.locator('.ant-select').first();
    await expect(dsSelect).toBeVisible({ timeout: 10000 });
    await dsSelect.click();
    // antd 6 的下拉：第一项
    const firstOption = page.locator('.ant-select-item-option').first();
    await expect(firstOption).toBeVisible({ timeout: 5000 });
    await firstOption.click();

    // 等到 "从数据库导入表" 按钮出现并 enabled（依赖 selectedDs）
    const discoverBtn = page.getByRole('button', { name: /从数据库导入表/ });
    await expect(discoverBtn).toBeEnabled({ timeout: 10000 });

    // 点之前先监听 message toast 出现（修复前是 silent no-op）
    await discoverBtn.click();

    // 修复后的契约：一定会有 toast (success/info/error) 或 alert
    // 修复前 bug：什么都不出现
    const feedback = page.locator('.ant-message-notice, .ant-message').first();
    await expect(feedback).toBeVisible({ timeout: 10000 });
  });

});
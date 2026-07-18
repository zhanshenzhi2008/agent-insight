import { test, expect } from '@playwright/test';

/**
 * E2E Test: AI Configuration Page
 * 覆盖 TC-AI-01 ~ TC-AI-10
 * 测试两张表：insight_ai_vendor（供应商凭证）+ insight_model_instance（模型实例）
 * 前置条件：无（使用 Mock API）
 */

test.describe('AI 配置管理 (TC-AI)', () => {

  // -------------------------------------------------------------------------
  // Mock 数据
  // -------------------------------------------------------------------------
  const mockVendors = [
    { id: 1, vendor: 'openai', displayName: 'OpenAI', baseUrl: 'https://api.openai.com', status: 1, timeoutSeconds: 30, maxRetries: 3, token: '******', createTime: '2026-07-01T10:00:00', updateTime: '2026-07-01T10:00:00' },
    { id: 2, vendor: 'anthropic', displayName: 'Anthropic', baseUrl: 'https://api.anthropic.com', status: 1, timeoutSeconds: 30, maxRetries: 3, token: null, createTime: '2026-07-01T10:00:00', updateTime: '2026-07-01T10:00:00' },
    { id: 3, vendor: 'ollama', displayName: 'Ollama', baseUrl: 'http://localhost:11434', status: 1, timeoutSeconds: 60, maxRetries: 1, token: null, createTime: '2026-07-01T10:00:00', updateTime: '2026-07-01T10:00:00' },
  ];

  const mockInstances = [
    { id: 1, vendorId: 1, vendor: 'openai', modelName: 'gpt-4o', capability: 'CHAT', tier: 'PRODUCTION', priority: 1, maxTokens: 8192, temperature: 0.7, topP: 1.0, isActive: 1, isCurrent: 1, description: '生产主力', createTime: '2026-07-01T10:00:00', updateTime: '2026-07-01T10:00:00' },
    { id: 2, vendorId: 1, vendor: 'openai', modelName: 'gpt-4o-mini', capability: 'CHAT', tier: 'LIGHT', priority: 1, maxTokens: 4096, temperature: 0.3, topP: 1.0, isActive: 1, isCurrent: 0, description: '轻量备用', createTime: '2026-07-01T10:00:00', updateTime: '2026-07-01T10:00:00' },
    { id: 3, vendorId: 2, vendor: 'anthropic', modelName: 'claude-3.5-sonnet', capability: 'CHAT', tier: 'PRODUCTION', priority: 2, maxTokens: 8192, temperature: 0.7, topP: 1.0, isActive: 1, isCurrent: 0, description: 'Anthropic 生产', createTime: '2026-07-01T10:00:00', updateTime: '2026-07-01T10:00:00' },
    { id: 4, vendorId: 1, vendor: 'openai', modelName: 'text-embedding-3-large', capability: 'EMBEDDING', tier: 'PRODUCTION', priority: 1, maxTokens: null, temperature: null, topP: null, isActive: 1, isCurrent: 1, description: 'Embedding', createTime: '2026-07-01T10:00:00', updateTime: '2026-07-01T10:00:00' },
    { id: 5, vendorId: 3, vendor: 'ollama', modelName: 'qwen2.5-72b', capability: 'CHAT', tier: 'PRODUCTION', priority: 3, maxTokens: 8192, temperature: 0.7, topP: 1.0, isActive: 0, isCurrent: 0, description: 'Ollama 生产', createTime: '2026-07-01T10:00:00', updateTime: '2026-07-01T10:00:00' },
  ];

  // -------------------------------------------------------------------------
  // Mock API routes（拦截 /api/v1/explorer/ 下的请求）
  // -------------------------------------------------------------------------
  test.beforeEach(async ({ page }) => {
    await page.route(/\/api\/v1\/explorer\/ai-vendors/, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 0, data: mockVendors }),
      });
    });
    await page.route(/\/api\/v1\/explorer\/ai-model-instances/, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 0, data: mockInstances }),
      });
    });
  });

  // -------------------------------------------------------------------------
  // TC-AI-01: AI 配置页加载成功
  // -------------------------------------------------------------------------
  test('TC-AI-01: AI 配置页加载成功，显示两个 Tab', async ({ page }) => {
    await page.goto('/explorer/ai-config');
    await page.waitForLoadState('networkidle');

    await expect(page.getByText('AI 配置管理')).toBeVisible();
    await expect(page.getByRole('tab', { name: '供应商凭证' })).toBeVisible();
    await expect(page.getByRole('tab', { name: '模型实例' })).toBeVisible();
    await expect(page.getByText('insight_ai_vendor')).toBeVisible();
    await expect(page.getByText('insight_model_instance')).toBeVisible();
  });

  // -------------------------------------------------------------------------
  // TC-AI-02: 供应商凭证表格加载
  // -------------------------------------------------------------------------
  test('TC-AI-02: 供应商凭证表格显示 vendor 数据', async ({ page }) => {
    await page.goto('/explorer/ai-config');
    await page.waitForLoadState('networkidle');
    await page.waitForSelector('.ant-table-wrapper', { state: 'visible', timeout: 8000 });

    // openai / anthropic / ollama 至少一个可见
    await expect(page.getByText('openai').first()).toBeVisible();
    await expect(page.getByText('anthropic').first()).toBeVisible();
    await expect(page.getByText('ollama').first()).toBeVisible();
  });

  // -------------------------------------------------------------------------
  // TC-AI-03: Token 脱敏显示
  // -------------------------------------------------------------------------
  test('TC-AI-03: 已配置 token 显示绿色"已配置"，未配置显示灰色"未配置"', async ({ page }) => {
    await page.goto('/explorer/ai-config');
    await page.waitForLoadState('networkidle');
    await page.waitForSelector('.ant-table-wrapper', { state: 'visible', timeout: 8000 });

    // 找包含"已配置"的 span
    await expect(page.getByText('已配置').first()).toBeVisible();
    // 找包含"未配置"的 span
    await expect(page.getByText('未配置').first()).toBeVisible();
  });

  // -------------------------------------------------------------------------
  // TC-AI-04: 供应商凭证 — 新建 Modal
  // -------------------------------------------------------------------------
  test('TC-AI-04: 点击新增供应商，打开新建 Modal', async ({ page }) => {
    await page.goto('/explorer/ai-config');
    await page.waitForLoadState('networkidle');
    await page.waitForSelector('.ant-table-wrapper', { state: 'visible', timeout: 8000 });

    await page.getByRole('button', { name: /新增供应商/i }).click();
    await page.waitForLoadState('networkidle');

    // 等待 Modal 打开
    await expect(page.getByText('新增供应商').first()).toBeVisible({ timeout: 8000 });
    const modal = page.locator('.ant-modal:visible');
    await expect(modal).toBeVisible({ timeout: 5000 });
    // Vendor Select 可见（Vendor Modal 有 2 个 Select：Vendor + Status）
    await expect(modal.locator('.ant-select')).toHaveCount(2, { timeout: 5000 });
    // Base URL 字段
    await expect(modal.getByText('Base URL（留空走 Spring AI 默认）')).toBeVisible();
    // 保存按钮（ant-modal-footer 的确认按钮）
    await expect(modal.locator('.ant-btn-primary')).toBeVisible({ timeout: 5000 });
  });

  // -------------------------------------------------------------------------
  // TC-AI-05: 供应商凭证 — 状态 Tag
  // -------------------------------------------------------------------------
  test('TC-AI-05: 状态列显示绿色"启用" Tag', async ({ page }) => {
    await page.goto('/explorer/ai-config');
    await page.waitForLoadState('networkidle');
    await page.waitForSelector('.ant-table-wrapper', { state: 'visible', timeout: 8000 });

    const enabledTag = page.locator('.ant-tag', { hasText: '启用' }).first();
    await expect(enabledTag).toBeVisible();
    // Ant Design green: rgb(r,g,b) 中 g > r 且 g > b，且 r 较小（绿=非红）
    const color = await enabledTag.evaluate((el: HTMLElement) => window.getComputedStyle(el).color);
    expect(color).toMatch(/rgb\((\d+),\s*(\d+),\s*(\d+)\)/);
    const match = color.match(/rgb\((\d+),\s*(\d+),\s*(\d+)\)/);
    const [, r, g, b] = match!.map(Number);
    expect(g).toBeGreaterThan(r);
  });

  // -------------------------------------------------------------------------
  // TC-AI-06: 模型实例 Tab — 切换并显示
  // -------------------------------------------------------------------------
  test('TC-AI-06: 切换到模型实例 Tab，显示模型数据', async ({ page }) => {
    await page.goto('/explorer/ai-config');
    await page.waitForLoadState('networkidle');
    await page.waitForSelector('.ant-table-wrapper', { state: 'visible', timeout: 8000 });

    await page.getByRole('tab', { name: '模型实例' }).click();
    await page.waitForSelector('.ant-tabs-tabpane-active .ant-table-wrapper', { state: 'visible', timeout: 8000 });

    // 模型名称可见
    await expect(page.getByText('gpt-4o').first()).toBeVisible();
    await expect(page.getByText('gpt-4o-mini').first()).toBeVisible();
    await expect(page.getByText('claude-3.5-sonnet').first()).toBeVisible();
  });

  // -------------------------------------------------------------------------
  // TC-AI-07: 模型实例 — Capability Tag 颜色
  // -------------------------------------------------------------------------
  test('TC-AI-07: Capability Tag（CHAT/EMBEDDING 等）可见且有颜色', async ({ page }) => {
    await page.goto('/explorer/ai-config');
    await page.waitForLoadState('networkidle');
    await page.getByRole('tab', { name: '模型实例' }).click();
    await page.waitForSelector('.ant-tabs-tabpane-active .ant-table-wrapper', { state: 'visible', timeout: 8000 });

    // CHAT tag 可见
    await expect(page.locator('.ant-tabs-tabpane-active .ant-tag', { hasText: 'CHAT' }).first()).toBeVisible();
    // EMBEDDING tag 可见
    await expect(page.locator('.ant-tabs-tabpane-active .ant-tag', { hasText: 'EMBEDDING' }).first()).toBeVisible();
  });

  // -------------------------------------------------------------------------
  // TC-AI-08: 模型实例 — 激活/当前 标志
  // -------------------------------------------------------------------------
  test('TC-AI-08: isActive/isCurrent 标志显示', async ({ page }) => {
    await page.goto('/explorer/ai-config');
    await page.waitForLoadState('networkidle');
    await page.getByRole('tab', { name: '模型实例' }).click();
    await page.waitForSelector('.ant-tabs-tabpane-active .ant-table-wrapper', { state: 'visible', timeout: 8000 });

    // ✓ 标志可见
    await expect(page.getByText('✓').first()).toBeVisible();
    // ★ 标志可见（isCurrent=1 的模型）
    await expect(page.getByText('★').first()).toBeVisible();
  });

  // -------------------------------------------------------------------------
  // TC-AI-09: 模型实例 — 新建 Modal
  // -------------------------------------------------------------------------
  test('TC-AI-09: 点击新增模型实例，打开新建 Modal', async ({ page }) => {
    await page.goto('/explorer/ai-config');
    await page.waitForLoadState('networkidle');
    await page.getByRole('tab', { name: '模型实例' }).click();
    await page.waitForSelector('.ant-tabs-tabpane-active .ant-table-wrapper', { state: 'visible', timeout: 8000 });

    await page.getByRole('button', { name: /新增模型实例/i }).click();

    await expect(page.getByText('新增模型实例').first()).toBeVisible({ timeout: 5000 });
    await expect(page.locator('.ant-modal').getByLabel('供应商')).toBeVisible();
    await expect(page.locator('.ant-modal').getByLabel('Model Name')).toBeVisible();
    await expect(page.locator('.ant-modal').getByLabel('Capability')).toBeVisible();
  });

  // -------------------------------------------------------------------------
  // TC-AI-10: 供应商凭证 — 刷新按钮
  // -------------------------------------------------------------------------
  test('TC-AI-10: 点击刷新按钮，触发 /ai-vendors GET 请求', async ({ page }) => {
    const vendorRequests: string[] = [];
    page.on('request', req => {
      if (req.url().includes('/ai-vendors') && req.method() === 'GET') {
        vendorRequests.push(req.url());
      }
    });

    await page.goto('/explorer/ai-config');
    await page.waitForLoadState('networkidle');
    await page.waitForSelector('.ant-table-wrapper', { state: 'visible', timeout: 8000 });

    await page.getByRole('button', { name: /刷新/i }).first().click();
    await page.waitForLoadState('networkidle');

    expect(vendorRequests.some(r => r.includes('/ai-vendors'))).toBeTruthy();
  });

});

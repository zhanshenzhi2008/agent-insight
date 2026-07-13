/// <reference types="vitest/globals" />

import '@testing-library/jest-dom';

// antd 响应式依赖 window.matchMedia，jsdom 默认没有实现
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

// antd Table/ResizeObserver 依赖
class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}
window.ResizeObserver = ResizeObserver;

// jsdom 没有实现 getComputedStyle
Object.defineProperty(window, 'getComputedStyle', {
  value: () => ({
    getPropertyValue: () => '',
    length: 0,
    item: () => '',
    get namedPropertyMap() { return new Map(); },
  }),
});

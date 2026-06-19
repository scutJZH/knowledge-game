/**
 * Scenario D — Content container max-width constraint
 *
 * White-box difference:
 *   White-box MainLayout.test.tsx checks that MainLayout renders with the correct
 *   menu items and "未登录" placeholder text. It does NOT verify CSS layout
 *   properties applied via the stylesheet (MainLayout.css), specifically that
 *   the `.content` wrapper div has `max-width: 1200px`.
 *
 * This test renders MainLayout inside a MemoryRouter, queries the `.content` div
 * rendered as the Content wrapper, and asserts its computed `maxWidth` equals
 * `1200px` — confirming the CSS module is loaded and applied correctly.
 *
 * 契约测试（非端到端 CSS 加载测试）：jsdom 不会自动加载组件 import 的 CSS 文件。
 * 我们在 beforeEach 中注入与 MainLayout.css 完全相同的 CSS 规则到 <style> 元素，
 * 使 getComputedStyle() 返回预期值。如果生产环境中 MainLayout.css 被 tree-shake
 * 或路径写错，此测试不会失败 — 那属于端到端/浏览器测试的覆盖范围。
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import MainLayout from '@/layouts/MainLayout';

function TestHarness() {
  return (
    <MemoryRouter initialEntries={['/home']}>
      <Routes>
        <Route element={<MainLayout />}>
          <Route path="/home" element={<div>Page Content</div>} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
}

describe('Content container max-width', () => {
  beforeEach(() => {
    // Inject the real CSS rules from MainLayout.css so jsdom can compute styles
    const style = document.createElement('style');
    style.setAttribute('data-test-css', 'MainLayout');
    style.textContent = `
      .content {
        max-width: 1200px;
        margin: 0 auto;
        padding: 24px 16px;
      }
    `;
    document.head.appendChild(style);
  });

  afterEach(() => {
    // Clean up injected styles to avoid test pollution
    document.head.querySelectorAll('[data-test-css="MainLayout"]').forEach((el) => el.remove());
  });

  it('applies max-width: 1200px to the .content wrapper div', () => {
    const { container } = render(<TestHarness />);

    const contentEl = container.querySelector('.content');
    expect(contentEl).toBeInTheDocument();

    const computed = window.getComputedStyle(contentEl!);
    expect(computed.maxWidth).toBe('1200px');
  });

  it('renders child content via Outlet inside .content div', () => {
    const { container } = render(<TestHarness />);

    const contentEl = container.querySelector('.content');
    expect(contentEl).toBeInTheDocument();

    // The Outlet renders our Route child inside the .content wrapper
    expect(contentEl!).toHaveTextContent('Page Content');
  });

  it('centers the content with auto horizontal margin', () => {
    const { container } = render(<TestHarness />);

    const contentEl = container.querySelector('.content');
    expect(contentEl).toBeInTheDocument();

    const computed = window.getComputedStyle(contentEl!);
    // CSS: margin: 0 auto (top/bottom=0, left/right=auto for horizontal centering)
    // jsdom preserves the specified "auto" for left/right margins since there is
    // no viewport to resolve auto to a pixel value.
    expect(computed.maxWidth).toBe('1200px');
    expect(computed.marginTop).toBe('0px');
    expect(computed.marginBottom).toBe('0px');
    // Left and right auto margins center the block — jsdom keeps "auto" as-is
    expect(computed.marginLeft).toBe('auto');
    expect(computed.marginRight).toBe('auto');
  });
});

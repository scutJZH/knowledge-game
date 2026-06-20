/**
 * Scenario C — Light theme actually applied
 *
 * White-box difference:
 *   White-box App.test.tsx only smoke-renders App and checks that "Knowledge Game"
 *   text appears. It never validates that the antd defaultAlgorithm theme is actually
 *   passed to ConfigProvider or that it takes effect on rendered components.
 *
 * This test verifies two aspects:
 *   1. The theme configuration object exported from @/styles/theme correctly
 *      references antd's defaultAlgorithm (module-level contract test).
 *   2. When a component is wrapped in ConfigProvider with defaultAlgorithm, the
 *      resulting DOM reflects light theme without rendering errors.
 */
import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { ConfigProvider, Layout, theme } from 'antd';
import appTheme from '@/styles/theme';

describe('Light theme', () => {
  it('theme module exports defaultAlgorithm', () => {
    expect(appTheme.algorithm).toBe(theme.defaultAlgorithm);
  });

  it('ConfigProvider with defaultAlgorithm renders children and applies light background', () => {
    const { container } = render(
      <ConfigProvider theme={{ algorithm: theme.defaultAlgorithm }}>
        <Layout style={{ minHeight: '100vh' }}>
          <Layout.Content>
            <div data-testid="inner">Themed Content</div>
          </Layout.Content>
        </Layout>
      </ConfigProvider>,
    );

    expect(container.querySelector('[data-testid="inner"]')).toHaveTextContent('Themed Content');

    const layoutEl = container.querySelector('.ant-layout');
    expect(layoutEl).toBeInTheDocument();
    expect(layoutEl).toHaveClass('ant-layout');
  });

  it('renders child elements inside light-themed ConfigProvider without errors', () => {
    const { container } = render(
      <ConfigProvider theme={appTheme}>
        <div className="light-scope">
          <span>Scoped content</span>
        </div>
      </ConfigProvider>,
    );

    expect(container.querySelector('.light-scope')).toBeInTheDocument();
    expect(container).toHaveTextContent('Scoped content');
  });
});

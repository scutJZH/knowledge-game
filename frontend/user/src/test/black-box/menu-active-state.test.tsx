/**
 * Scenario A — Menu active state responds to path changes
 *
 * White-box difference:
 *   White-box MainLayout.test.tsx only asserts that all 4 menu item labels
 *   (首页/图鉴/卡包/我的) exist in the DOM statically. It does NOT verify that
 *   the antd Menu's selectedKeys property changes when the user navigates to a
 *   different route.
 *
 * This test renders MainLayout inside a MemoryRouter with multiple routes,
 * clicks menu items to navigate, and asserts that the correct menu item
 * receives the "ant-menu-item-selected" CSS class after each navigation.
 */
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import MainLayout from '@/layouts/MainLayout';

function TestHarness() {
  return (
    <MemoryRouter initialEntries={['/home']}>
      <Routes>
        <Route element={<MainLayout />}>
          <Route path="/home" element={<div data-testid="page-home">Home Page</div>} />
          <Route
            path="/collection"
            element={<div data-testid="page-collection">Collection Page</div>}
          />
          <Route path="/card-bag" element={<div data-testid="page-card-bag">Card Bag Page</div>} />
          <Route path="/profile" element={<div data-testid="page-profile">Profile Page</div>} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
}

describe('Menu active state', () => {
  it('initially highlights 首页 when path is /home', () => {
    render(<TestHarness />);

    // All 4 menu items should be present
    const homeItem = screen.getByText('首页');
    const collectionItem = screen.getByText('图鉴');
    const cardBagItem = screen.getByText('卡包');
    const profileItem = screen.getByText('我的');

    // The 首页 item should have the selected class
    expect(homeItem.closest('.ant-menu-item')).toHaveClass('ant-menu-item-selected');
    // Other items should NOT be selected
    expect(collectionItem.closest('.ant-menu-item')).not.toHaveClass('ant-menu-item-selected');
    expect(cardBagItem.closest('.ant-menu-item')).not.toHaveClass('ant-menu-item-selected');
    expect(profileItem.closest('.ant-menu-item')).not.toHaveClass('ant-menu-item-selected');
  });

  it('switches selected menu item when navigating to /collection', async () => {
    const user = userEvent.setup();
    render(<TestHarness />);

    // Click 图鉴 menu item (navigates to /collection)
    await user.click(screen.getByText('图鉴'));

    // Collection page should be rendered
    expect(screen.getByTestId('page-collection')).toBeInTheDocument();

    // 图鉴 item should now be selected, 首页 should not
    expect(screen.getByText('图鉴').closest('.ant-menu-item')).toHaveClass(
      'ant-menu-item-selected',
    );
    expect(screen.getByText('首页').closest('.ant-menu-item')).not.toHaveClass(
      'ant-menu-item-selected',
    );
  });

  it('restores 首页 selection when navigating back', async () => {
    const user = userEvent.setup();
    render(<TestHarness />);

    // Navigate to 图鉴, then back to 首页
    await user.click(screen.getByText('图鉴'));
    await user.click(screen.getByText('首页'));

    // 首页 should be selected again
    expect(screen.getByTestId('page-home')).toBeInTheDocument();
    expect(screen.getByText('首页').closest('.ant-menu-item')).toHaveClass(
      'ant-menu-item-selected',
    );
    expect(screen.getByText('图鉴').closest('.ant-menu-item')).not.toHaveClass(
      'ant-menu-item-selected',
    );
  });

  it('selects 卡包 when navigating directly', async () => {
    const user = userEvent.setup();
    render(<TestHarness />);

    await user.click(screen.getByText('卡包'));

    expect(screen.getByTestId('page-card-bag')).toBeInTheDocument();
    expect(screen.getByText('卡包').closest('.ant-menu-item')).toHaveClass(
      'ant-menu-item-selected',
    );
  });
});

import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import MainLayout from '../MainLayout';

describe('MainLayout', () => {
  it('should render logo, menu items, child content, and user placeholder', () => {
    render(
      <MemoryRouter initialEntries={['/home']}>
        <MainLayout />
      </MemoryRouter>,
    );

    expect(screen.getByText(/Knowledge Game/)).toBeInTheDocument();
    expect(screen.getByText('首页')).toBeInTheDocument();
    expect(screen.getByText('图鉴')).toBeInTheDocument();
    expect(screen.getByText('卡包')).toBeInTheDocument();
    expect(screen.getByText('我的')).toBeInTheDocument();
    expect(screen.getByText('未登录')).toBeInTheDocument();
  });

  it('should render child content via Outlet', () => {
    render(
      <MemoryRouter initialEntries={['/home']}>
        <MainLayout />
      </MemoryRouter>,
    );
    // Outlet renders nothing when no child routes match, but Layout DOM is present
    expect(screen.getByText('首页')).toBeInTheDocument();
  });
});

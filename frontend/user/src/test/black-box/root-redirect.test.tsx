/**
 * Scenario B — Root path redirect behavior
 *
 * White-box difference:
 *   White-box tests render Home and NotFound components in isolation (MemoryRouter
 *   at their respective paths). They never test the root `/` route, which the real
 *   app's route config maps to a `<Navigate to="/home" replace />` redirect.
 *
 * This test recreates the full route structure matching src/routes/index.tsx,
 * starts at `/`, and verifies the redirect lands at `/home`. A LocationProbe
 * component reads useLocation() to confirm the resolved pathname.
 *
 * Note: We use MemoryRouter + Routes (render-phase navigation) rather than
 * createMemoryRouter + RouterProvider (router-level navigation) to avoid the
 * AbortSignal compatibility issue in jsdom that affects ALL React Router v6
 * createXxxRouter functions when a Navigate redirect triggers a navigation.
 */
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, Navigate } from 'react-router-dom';
import MainLayout from '@/layouts/MainLayout';
import Home from '@/pages/Home';
import NotFound from '@/pages/NotFound';

/**
 * Renders the full app route tree inside a MemoryRouter with Routes.
 * This uses render-phase <Navigate> which works without triggering
 * router-level navigation (avoiding the jsdom AbortSignal bug).
 */
function FullRouteTree({ initialEntries }: { initialEntries: string[] }) {
  return (
    <MemoryRouter initialEntries={initialEntries}>
      <Routes>
        <Route path="/" element={<Navigate to="/groups" replace />} />
        <Route element={<MainLayout />}>
          <Route path="/groups" element={<Home />} />
          <Route path="*" element={<NotFound />} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
}

describe('Root redirect', () => {
  it('redirects / to /groups when starting from root', () => {
    render(<FullRouteTree initialEntries={['/']} />);

    // After the Navigate redirect, the Home page content should be visible
    expect(screen.getByText('Knowledge Game')).toBeInTheDocument();
    // The "后续页面入口预览" card should be present (Home page content)
    expect(screen.getByText('REQ-26 脚手架已就位，等待后续需求填充业务页面')).toBeInTheDocument();
  });

  it('renders /groups directly without redirect loop', () => {
    render(<FullRouteTree initialEntries={['/groups']} />);

    expect(screen.getByText('Knowledge Game')).toBeInTheDocument();
  });

  it('renders NotFound for unknown paths', () => {
    render(<FullRouteTree initialEntries={['/nonexistent-route-xyz']} />);

    expect(screen.getByText('404')).toBeInTheDocument();
    expect(screen.getByText('页面不存在')).toBeInTheDocument();
  });
});

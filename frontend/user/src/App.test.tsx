import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import appTheme from './styles/theme';
import Home from './pages/Home';

function TestApp() {
  const router = createMemoryRouter([{ path: '/', element: <Home /> }], { initialEntries: ['/'] });
  return (
    <ConfigProvider theme={appTheme}>
      <RouterProvider router={router} />
    </ConfigProvider>
  );
}

describe('App', () => {
  it('should render without crashing', () => {
    render(<TestApp />);
    expect(screen.getByText(/Knowledge Game/i)).toBeInTheDocument();
  });
});

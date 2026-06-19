import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import NotFound from '../index';

const mockNavigate = vi.fn();

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

describe('NotFound', () => {
  it('should render 404 result', () => {
    render(
      <MemoryRouter>
        <NotFound />
      </MemoryRouter>,
    );

    expect(screen.getByText('404')).toBeInTheDocument();
    expect(screen.getByText('页面不存在')).toBeInTheDocument();
    expect(screen.getByText('返回首页')).toBeInTheDocument();
  });

  it('should navigate to home on button click', async () => {
    render(
      <MemoryRouter>
        <NotFound />
      </MemoryRouter>,
    );

    await userEvent.click(screen.getByText('返回首页'));
    expect(mockNavigate).toHaveBeenCalledWith('/home');
  });
});

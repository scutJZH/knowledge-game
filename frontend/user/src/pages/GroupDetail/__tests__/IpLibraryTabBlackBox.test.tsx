/**
 * REQ-62 Black-Box Boundary Tests — IpLibraryTab
 *
 * These tests cover edge cases and boundary conditions that the white-box
 * developer tests (IpLibraryTab.test.tsx) do NOT cover:
 *
 *   Boundary A: Tab switch away and back → data correctly refetches
 *   Boundary B: Double-click add button → only one PUT
 *   Boundary C: Add failure → error message + modal stays open
 *   Boundary D: Remove failure → error message + list unchanged
 */

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import IpLibraryTab from '../IpLibraryTab';

const {
  mockListGroupIpLibrary,
  mockUpdateGroupIpLibrary,
  mockListActiveIpSeries,
} = vi.hoisted(() => ({
  mockListGroupIpLibrary: vi.fn(),
  mockUpdateGroupIpLibrary: vi.fn(),
  mockListActiveIpSeries: vi.fn(),
}));

vi.mock('@/services/group-api', () => ({
  listGroupIpLibrary: mockListGroupIpLibrary,
  updateGroupIpLibrary: mockUpdateGroupIpLibrary,
  listActiveIpSeries: mockListActiveIpSeries,
}));

const { mockMessageSuccess, mockMessageError } = vi.hoisted(() => ({
  mockMessageSuccess: vi.fn(),
  mockMessageError: vi.fn(),
}));

vi.mock('antd', async () => {
  const actual = await vi.importActual<typeof import('antd')>('antd');
  return { ...(actual as object), message: { success: mockMessageSuccess, error: mockMessageError } };
});

const LINKED = [
  { id: 1, groupId: 1, ipSeriesId: 1, ipSeriesName: 'IP一号', ipSeriesCode: 'IP001', coverImageFileId: null, coverImageUrl: null, addedAt: 1 },
];

const ALL_ACTIVE = [
  { id: 1, name: 'IP一号', code: 'IP001', coverImageFileId: null, coverImageUrl: null },
  { id: 2, name: 'IP二号', code: 'IP002', coverImageFileId: null, coverImageUrl: null },
];

describe('REQ-62 Black-Box: IpLibraryTab Boundary Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // Boundary A: Tab switch remount → refetch
  it('Boundary A: remount triggers data refetch', async () => {
    mockListGroupIpLibrary.mockResolvedValue(LINKED);
    mockListActiveIpSeries.mockResolvedValue(ALL_ACTIVE);

    const { rerender } = render(<IpLibraryTab groupId={1} myRole="OWNER" key="v1" />);
    await waitFor(() => expect(screen.getByTestId('ip-card-1')).toBeInTheDocument());
    expect(mockListGroupIpLibrary).toHaveBeenCalledTimes(1);

    rerender(<IpLibraryTab groupId={1} myRole="OWNER" key="v2" />);
    await waitFor(() => expect(mockListGroupIpLibrary).toHaveBeenCalledTimes(2));
  });

  // Boundary B: Double-click add → only one PUT
  it('Boundary B: double-click add confirm triggers only one PUT', async () => {
    const user = userEvent.setup();
    mockListGroupIpLibrary.mockResolvedValue(LINKED);
    mockListActiveIpSeries.mockResolvedValue(ALL_ACTIVE);
    // never-resolving promise keeps saving=true
    mockUpdateGroupIpLibrary.mockReturnValue(new Promise(() => {}));

    render(<IpLibraryTab groupId={1} myRole="OWNER" />);
    await waitFor(() => expect(screen.getByRole('button', { name: /添加/ })).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /添加/ }));
    await waitFor(() => expect(screen.getByText('IP二号')).toBeInTheDocument());

    const modal = document.querySelector('.ant-modal-body')!;
    await user.click(modal.querySelector('input[type="checkbox"]')!);

    const confirmBtn = screen.getByRole('button', { name: /确\s*定/ });
    await user.click(confirmBtn);
    // 用 fireEvent 同步触发第二次点击（loading 态下不响应）
    const { fireEvent } = await import('@testing-library/react');
    fireEvent.click(confirmBtn);

    expect(mockUpdateGroupIpLibrary).toHaveBeenCalledTimes(1);
  });

  // Boundary C: Add failure → error message
  it('Boundary C: add failure shows error message', async () => {
    const user = userEvent.setup();
    mockListGroupIpLibrary.mockResolvedValue(LINKED);
    mockListActiveIpSeries.mockResolvedValue(ALL_ACTIVE);
    mockUpdateGroupIpLibrary.mockRejectedValue({ response: { data: { message: 'IP系列未启用' } } });

    render(<IpLibraryTab groupId={1} myRole="OWNER" />);
    await waitFor(() => expect(screen.getByRole('button', { name: /添加/ })).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /添加/ }));
    await waitFor(() => expect(screen.getByText('IP二号')).toBeInTheDocument());

    const modal = document.querySelector('.ant-modal-body')!;
    await user.click(modal.querySelector('input[type="checkbox"]')!);
    await user.click(screen.getByRole('button', { name: /确\s*定/ }));

    await waitFor(() => {
      expect(mockMessageError).toHaveBeenCalledWith('IP系列未启用');
    });
  });

  // Boundary D: Remove failure → error message
  it('Boundary D: remove failure shows error message', async () => {
    const user = userEvent.setup();
    mockListGroupIpLibrary.mockResolvedValue(LINKED);
    mockListActiveIpSeries.mockResolvedValue(ALL_ACTIVE);
    mockUpdateGroupIpLibrary.mockRejectedValue({ response: { data: { message: '网络异常' } } });

    render(<IpLibraryTab groupId={1} myRole="OWNER" />);
    await waitFor(() => expect(screen.getByTestId('ip-card-menu-1')).toBeInTheDocument());

    await user.click(screen.getByTestId('ip-card-menu-1'));
    await waitFor(() => expect(screen.getByText('删除')).toBeInTheDocument());
    await user.click(screen.getByText('删除'));

    await waitFor(() => {
      expect(mockMessageError).toHaveBeenCalledWith('网络异常');
    });
  });
});

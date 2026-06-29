/**
 * REQ-62 Black-Box Boundary Tests — IpLibraryTab
 *
 * These tests cover edge cases and boundary conditions that the white-box
 * developer tests (IpLibraryTab.test.tsx) do NOT cover:
 *
 *   Boundary A: Tab switch away and back → data correctly refreshes
 *     (state isolation across re-mounts, not single-render operations)
 *
 *   Boundary B: Double-click save button → only one PUT call
 *     (concurrent save prevention, not normal save flow)
 *
 *   Boundary C: PUT save failure → error message displayed + selectedIds preserved
 *     (save rejection state preservation, not Promise.all load failure)
 *
 *   Boundary D: Empty active IP list → specific empty text
 *     (exact copy text verification, not general empty state rendering)
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import IpLibraryTab from '../IpLibraryTab';
import type { GroupIpLibraryResponse, ActiveIpSeriesResponse } from '@/types/group';

// ── Hoisted mock references ────────────────────────────────────────────
const {
  mockMessageSuccess,
  mockMessageError,
  mockListGroupIpLibrary,
  mockUpdateGroupIpLibrary,
  mockListActiveIpSeries,
} = vi.hoisted(() => ({
  mockMessageSuccess: vi.fn(),
  mockMessageError: vi.fn(),
  mockListGroupIpLibrary: vi.fn(),
  mockUpdateGroupIpLibrary: vi.fn(),
  mockListActiveIpSeries: vi.fn(),
}));

// ── Module mocks ───────────────────────────────────────────────────────
vi.mock('@/services/group-api', () => ({
  listGroupIpLibrary: mockListGroupIpLibrary,
  updateGroupIpLibrary: mockUpdateGroupIpLibrary,
  listActiveIpSeries: mockListActiveIpSeries,
}));

vi.mock('antd', async () => {
  const actual = await vi.importActual('antd');
  return {
    ...(actual as object),
    message: { success: mockMessageSuccess, error: mockMessageError },
  };
});

// ── Test fixtures ──────────────────────────────────────────────────────
function makeActiveIp(
  overrides: Partial<ActiveIpSeriesResponse> = {},
): ActiveIpSeriesResponse {
  return {
    id: 1,
    name: 'IP一号',
    code: 'IP001',
    coverImageFileId: null,
    coverImageUrl: null,
    ...overrides,
  };
}

// ═══════════════════════════════════════════════════════════════════════
// Tests
// ═══════════════════════════════════════════════════════════════════════
describe('REQ-62 Black-Box: IpLibraryTab Boundary Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // =====================================================================
  // Boundary A: Tab switch away and back → data correctly refreshes
  // =====================================================================
  it('Boundary A: switching tab away and back re-fetches fresh data (not stale)', async () => {
    mockListGroupIpLibrary.mockResolvedValue([]);

    // First mount: one active IP
    mockListActiveIpSeries.mockResolvedValueOnce([
      makeActiveIp({ id: 1, name: 'IP一号' }),
    ]);

    const { rerender } = render(
      <IpLibraryTab groupId={1} myRole="OWNER" key="tab-visit-1" />,
    );

    await waitFor(() => {
      expect(screen.getByText('IP一号')).toBeInTheDocument();
    });
    expect(mockListActiveIpSeries).toHaveBeenCalledTimes(1);

    // Second mount (simulates navigating away to another tab and back —
    // React key change forces unmount + remount, triggering useEffect again)
    mockListActiveIpSeries.mockResolvedValueOnce([
      makeActiveIp({ id: 2, name: 'IP二号' }),
    ]);

    rerender(<IpLibraryTab groupId={1} myRole="OWNER" key="tab-visit-2" />);

    // Verify API is called again (fresh fetch, not stale data)
    await waitFor(() => {
      expect(mockListActiveIpSeries).toHaveBeenCalledTimes(2);
    });

    // Verify new data (IP二号, not stale IP一号)
    await waitFor(() => {
      expect(screen.getByText('IP二号')).toBeInTheDocument();
    });
    expect(screen.queryByText('IP一号')).not.toBeInTheDocument();
  });

  // =====================================================================
  // Boundary B: Double-click save button → only one PUT call
  // =====================================================================
  it('Boundary B: double-click save while saving triggers only one PUT call', async () => {
    const user = userEvent.setup();

    mockListGroupIpLibrary.mockResolvedValue([]);
    mockListActiveIpSeries.mockResolvedValue([
      makeActiveIp({ id: 1, name: 'IP一号' }),
    ]);

    // The update promise never resolves during this test — the component
    // stays in "saving" (loading) state, so the second click lands on a
    // loading button and must be ignored.
    const updatePromise = new Promise<GroupIpLibraryResponse[]>(() => {
      /* intentionally never resolves */
    });
    mockUpdateGroupIpLibrary.mockReturnValue(updatePromise);

    render(<IpLibraryTab groupId={1} myRole="OWNER" />);

    await waitFor(() => {
      expect(screen.getByTestId('ip-card-1')).toBeInTheDocument();
    });

    // Toggle IP1 on (click card to check the checkbox)
    await user.click(screen.getByTestId('ip-card-1'));

    const saveBtn = screen.getByRole('button', { name: /保存/ });
    expect(saveBtn).not.toBeDisabled();

    // Click save — fires PUT, component enters loading state
    await user.click(saveBtn);

    await waitFor(() => {
      expect(mockUpdateGroupIpLibrary).toHaveBeenCalledTimes(1);
    });

    // Second click on a loading button — must NOT trigger another PUT
    // Use fireEvent.click for synchronous dispatch to ensure the click
    // reaches the button before any async re-render could unset loading
    fireEvent.click(saveBtn);

    expect(mockUpdateGroupIpLibrary).toHaveBeenCalledTimes(1);
  });

  // =====================================================================
  // Boundary C: PUT save failure → error message + selectedIds preserved
  // =====================================================================
  it('Boundary C: PUT failure shows error message and preserves selectedIds for retry', async () => {
    const user = userEvent.setup();

    mockListGroupIpLibrary.mockResolvedValue([]);
    mockListActiveIpSeries.mockResolvedValue([
      makeActiveIp({ id: 1, name: 'IP一号' }),
      makeActiveIp({ id: 2, name: 'IP二号' }),
    ]);

    mockUpdateGroupIpLibrary.mockRejectedValue({
      response: { data: { message: 'IP系列未启用' } },
    });

    render(<IpLibraryTab groupId={1} myRole="OWNER" />);

    await waitFor(() => {
      expect(screen.getByTestId('ip-card-1')).toBeInTheDocument();
    });

    // Check IP1 to enable save
    await user.click(screen.getByTestId('ip-card-1'));

    const saveBtn = screen.getByRole('button', { name: /保存/ });
    await user.click(saveBtn);

    // Verify error message was displayed with the server's message
    await waitFor(() => {
      expect(mockMessageError).toHaveBeenCalledWith('IP系列未启用');
    });

    // Verify success was NOT called
    expect(mockMessageSuccess).not.toHaveBeenCalled();

    // Verify selectedIds are PRESERVED (checkbox for IP1 still checked)
    // — user can retry without re-selecting
    await waitFor(() => {
      const checkbox1 = within(screen.getByTestId('ip-card-1')).getByRole(
        'checkbox',
      );
      expect(checkbox1).toBeChecked();
    });
  });

  // =====================================================================
  // Boundary D: Empty active IP list → specific empty text
  // =====================================================================
  it('Boundary D: empty active IP list shows exact copy "系统中暂无可用 IP 系列"', async () => {
    mockListGroupIpLibrary.mockResolvedValue([]);
    mockListActiveIpSeries.mockResolvedValue([]);

    render(<IpLibraryTab groupId={1} myRole="OWNER" />);

    await waitFor(() => {
      expect(
        screen.getByText('系统中暂无可用 IP 系列'),
      ).toBeInTheDocument();
    });

    // No cards should be rendered
    expect(screen.queryByTestId(/^ip-card-/)).not.toBeInTheDocument();
  });
});

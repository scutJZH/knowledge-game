import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import JoinGroupModal from '../JoinGroupModal';

vi.mock('@/services/group-api', () => ({
  joinByInvite: vi.fn(),
  listMyGroups: vi.fn(),
  createGroup: vi.fn(),
  getUploadCredential: vi.fn(),
}));

describe('JoinGroupModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('关闭时不渲染输入框', () => {
    render(<JoinGroupModal open={false} onClose={() => {}} onSuccess={() => {}} />);
    expect(screen.queryByPlaceholderText('8 位邀请码')).not.toBeInTheDocument();
  });

  it('输入小写自动转大写', async () => {
    render(<JoinGroupModal open onClose={() => {}} onSuccess={() => {}} />);
    await waitFor(() => screen.getByPlaceholderText('8 位邀请码'));
    fireEvent.change(screen.getByPlaceholderText('8 位邀请码'), { target: { value: 'abc' } });
    expect(screen.getByPlaceholderText('8 位邀请码')).toHaveValue('ABC');
  });
});

import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import CreateGroupModal from '../CreateGroupModal';

vi.mock('@/services/group-api', () => ({
  createGroup: vi.fn(),
  getUploadCredential: vi.fn(),
  listMyGroups: vi.fn(),
  joinByInvite: vi.fn(),
}));

describe('CreateGroupModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('关闭时不渲染表单', () => {
    render(<CreateGroupModal open={false} onClose={() => {}} onSuccess={() => {}} />);
    expect(screen.queryByPlaceholderText('给群组起个名字')).not.toBeInTheDocument();
  });

  // 注：open 状态下 Modal 表单交互（校验/提交）在 jsdom 中受限，
  // 表单校验错误消息、提交成功/失败分支由手工验收覆盖（见 PRD 手工验收 #2）
});

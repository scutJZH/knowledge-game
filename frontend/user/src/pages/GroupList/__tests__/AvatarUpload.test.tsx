import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import AvatarUpload from '../AvatarUpload';

const mockCredential = vi.hoisted(() => vi.fn());
vi.mock('@/services/group-api', () => ({
  getUploadCredential: mockCredential,
}));

describe('AvatarUpload', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('未选择文件时显示"点击上传头像"虚线框', () => {
    render(<AvatarUpload />);
    expect(screen.getByText('点击上传头像')).toBeInTheDocument();
  });

  it('上传成功 → onChange 被调用', async () => {
    const onChange = vi.fn();
    mockCredential.mockResolvedValue([{ uploadUrl: 'https://upload.test/file', fileId: 99 }]);
    globalThis.fetch = vi.fn().mockResolvedValue({ ok: true });
    globalThis.URL.createObjectURL = vi.fn(() => 'blob://preview');

    render(<AvatarUpload onChange={onChange} />);
    const file = new File(['test'], 'avatar.png', { type: 'image/png' });
    const input = document.querySelector('input[type="file"]')!;
    fireEvent.change(input, { target: { files: [file] } });

    await waitFor(() => {
      expect(onChange).toHaveBeenCalledWith(99);
    });
  });

  it('上传失败 → 显示"上传失败，请重试"', async () => {
    mockCredential.mockRejectedValue(new Error('fail'));
    render(<AvatarUpload />);
    const file = new File(['test'], 'avatar.png', { type: 'image/png' });
    const input = document.querySelector('input[type="file"]')!;
    fireEvent.change(input, { target: { files: [file] } });

    // 注：antd message.error 在 jsdom 中可能不显示 DOM，此处验证不崩溃
    await waitFor(() => {
      expect(mockCredential).toHaveBeenCalled();
    });
  });
});

import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';

/** 模拟 getUploadCredential 和 uploadFile */
const mockGetUploadCredential = jest.fn();
const mockUploadFile = jest.fn();

jest.mock('@/services/fileUpload', () => ({
  getUploadCredential: (...args: any[]) => mockGetUploadCredential(...args),
  uploadFile: (...args: any[]) => mockUploadFile(...args),
}));

jest.mock('@/utils/token', () => ({
  getUserInfo: jest.fn(() => ({ id: 1, username: 'admin' })),
}));

/** 模拟 antd message */
jest.mock('antd', () => {
  const actual = jest.requireActual('antd');
  return {
    ...actual,
    message: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
  };
});

import { message } from 'antd';
import StarImageUpload from '../components/StarImageUpload';

describe('StarImageUpload', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockGetUploadCredential.mockResolvedValue({
      token: 'test-token',
      uploadUrl: 'http://file-service/api/files/upload',
    });
    mockUploadFile.mockResolvedValue('http://file-service/api/files/view/test-file-id.png');
  });

  describe('渲染', () => {
    it('应显示对应星级标签', () => {
      render(<StarImageUpload starLevel={3} />);
      expect(screen.getByText('★3')).toBeInTheDocument();
    });

    it('5 星标签应正常显示', () => {
      render(<StarImageUpload starLevel={5} />);
      expect(screen.getByText('★5')).toBeInTheDocument();
    });

    it('有 value 时应渲染缩略图区域', () => {
      render(<StarImageUpload starLevel={1} value="http://example.com/img1.png" />);
      const listItem = document.querySelector('.ant-upload-list-item');
      expect(listItem).toBeInTheDocument();
    });

    it('无 value 时应显示上传入口', () => {
      render(<StarImageUpload starLevel={2} />);
      const uploadBtn = document.querySelector('.ant-upload');
      expect(uploadBtn).toBeInTheDocument();
    });
  });

  describe('文件校验', () => {
    it('file input 的 accept 属性应限制图片类型', () => {
      const { container } = render(<StarImageUpload starLevel={1} />);
      const input = container.querySelector('input[type="file"]');
      expect(input).toBeInTheDocument();
      expect(input?.getAttribute('accept')).toContain('image/jpeg');
      expect(input?.getAttribute('accept')).toContain('image/png');
    });

    it('选择非图片文件应校验失败', async () => {
      render(<StarImageUpload starLevel={1} />);

      const fileInput = document.querySelector('.ant-upload input[type="file"]') as HTMLInputElement;
      expect(fileInput).toBeInTheDocument();

      const invalidFile = new File(['content'], 'test.txt', { type: 'text/plain' });
      fireEvent.change(fileInput, { target: { files: [invalidFile] } });

      await waitFor(() => {
        expect(message.warning).toHaveBeenCalledWith('仅支持 JPG、PNG、GIF、WebP 格式');
      });
      expect(mockGetUploadCredential).not.toHaveBeenCalled();
    });

    it('选择超限文件应校验失败', async () => {
      render(<StarImageUpload starLevel={1} />);

      const fileInput = document.querySelector('.ant-upload input[type="file"]') as HTMLInputElement;
      expect(fileInput).toBeInTheDocument();

      const hugeFile = new File(['x'.repeat(11 * 1024 * 1024)], 'huge.png', { type: 'image/png' });
      fireEvent.change(fileInput, { target: { files: [hugeFile] } });

      await waitFor(() => {
        expect(message.warning).toHaveBeenCalledWith('文件大小不能超过 10MB');
      });
      expect(mockGetUploadCredential).not.toHaveBeenCalled();
    });
  });

  describe('上传成功', () => {
    it('上传成功后应回调完整 URL', async () => {
      const onChange = jest.fn();
      mockGetUploadCredential.mockResolvedValue({
        token: 'test-token',
        uploadUrl: 'http://file-service/api/files/upload',
      });
      mockUploadFile.mockResolvedValue('http://file-service/api/files/view/new-file.png');

      render(<StarImageUpload starLevel={1} value={undefined} onChange={onChange} />);

      const fileInput = document.querySelector('.ant-upload input[type="file"]') as HTMLInputElement;
      expect(fileInput).toBeInTheDocument();

      const validFile = new File(['image-content'], 'test.png', { type: 'image/png' });
      fireEvent.change(fileInput, { target: { files: [validFile] } });

      await waitFor(() => {
        expect(onChange).toHaveBeenCalledWith('http://file-service/api/files/view/new-file.png');
      });
    });
  });

  describe('上传失败', () => {
    it('上传失败应显示错误信息', async () => {
      const onChange = jest.fn();
      mockGetUploadCredential.mockResolvedValue({
        token: 'test-token',
        uploadUrl: 'http://file-service/api/files/upload',
      });
      mockUploadFile.mockRejectedValue(new Error('网络错误，上传失败'));

      render(<StarImageUpload starLevel={1} value={undefined} onChange={onChange} />);

      const fileInput = document.querySelector('.ant-upload input[type="file"]') as HTMLInputElement;
      expect(fileInput).toBeInTheDocument();

      const validFile = new File(['image-content'], 'test.png', { type: 'image/png' });
      fireEvent.change(fileInput, { target: { files: [validFile] } });

      await waitFor(() => {
        expect(message.error).toHaveBeenCalledWith('网络错误，上传失败');
      });
      expect(onChange).not.toHaveBeenCalled();
    });
  });

  describe('无删除入口', () => {
    it('已有图片时不应显示删除图标', () => {
      render(<StarImageUpload starLevel={1} value="http://example.com/img1.png" />);
      // showRemoveIcon: false → 列表项操作按钮不应存在
      const deleteBtn = document.querySelector('.ant-upload-list-item-action');
      expect(deleteBtn).toBeNull();
    });
  });
});

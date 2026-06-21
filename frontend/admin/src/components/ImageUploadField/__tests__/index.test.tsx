import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';

/** 模拟 getUploadCredential 和 uploadFile */
const mockGetUploadCredential = jest.fn();
const mockUploadFile = jest.fn();

jest.mock('@/services/fileUpload', () => ({
  getUploadCredential: (...args: any[]) => mockGetUploadCredential(...args),
  uploadFile: (...args: any[]) => mockUploadFile(...args),
}));

const mockGetUserInfo = jest.fn<{ id: number; username: string } | null, []>(() => ({ id: 1, username: 'admin' }));

jest.mock('@/utils/token', () => ({
  getUserInfo: () => mockGetUserInfo(),
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
import ImageUploadField from '../index';

describe('ImageUploadField', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockGetUploadCredential.mockResolvedValue({
      token: 'test-token',
      uploadUrl: 'http://file-service/api/files/upload',
    });
    mockUploadFile.mockResolvedValue('http://file-service/api/files/view/test-file-id.png');
    mockGetUserInfo.mockReturnValue({ id: 1, username: 'admin' });
  });

  describe('渲染', () => {
    it('无 value 时应显示上传占位和默认文字', () => {
      render(<ImageUploadField bizType="CATEGORY_ICON" />);
      expect(screen.getByText('上传图片')).toBeInTheDocument();
    });

    it('无 value 时应显示自定义 placeholder', () => {
      render(<ImageUploadField bizType="CATEGORY_ICON" placeholder="上传图标" />);
      expect(screen.getByText('上传图标')).toBeInTheDocument();
    });

    it('有 value 时应渲染缩略图', () => {
      render(<ImageUploadField bizType="IP_SERIES" value={1} />);
      const listItem = document.querySelector('.ant-upload-list-item');
      expect(listItem).toBeInTheDocument();
    });

    it('file input 的 accept 属性应使用默认类型', () => {
      const { container } = render(<ImageUploadField bizType="IP_SERIES" />);
      const input = container.querySelector('input[type="file"]');
      expect(input?.getAttribute('accept')).toContain('image/jpeg');
      expect(input?.getAttribute('accept')).toContain('image/png');
      expect(input?.getAttribute('accept')).toContain('image/gif');
      expect(input?.getAttribute('accept')).toContain('image/webp');
    });

    it('file input 的 accept 属性应使用自定义类型', () => {
      const { container } = render(
        <ImageUploadField bizType="IP_SERIES" acceptTypes={['image/png']} />,
      );
      const input = container.querySelector('input[type="file"]');
      expect(input?.getAttribute('accept')).toBe('image/png');
    });
  });

  describe('文件校验', () => {
    it('选择非图片文件应校验失败并显示 warning', async () => {
      render(<ImageUploadField bizType="CATEGORY_ICON" />);

      const fileInput = document.querySelector('.ant-upload input[type="file"]') as HTMLInputElement;
      expect(fileInput).toBeInTheDocument();

      const invalidFile = new File(['content'], 'test.txt', { type: 'text/plain' });
      fireEvent.change(fileInput, { target: { files: [invalidFile] } });

      await waitFor(() => {
        expect(message.warning).toHaveBeenCalledWith('仅支持 JPG、PNG、GIF、WebP 格式');
      });
      expect(mockGetUploadCredential).not.toHaveBeenCalled();
    });

    it('选择超限文件应校验失败并显示 warning', async () => {
      render(<ImageUploadField bizType="CATEGORY_ICON" />);

      const fileInput = document.querySelector('.ant-upload input[type="file"]') as HTMLInputElement;
      expect(fileInput).toBeInTheDocument();

      const hugeFile = new File(['x'.repeat(11 * 1024 * 1024)], 'huge.png', { type: 'image/png' });
      fireEvent.change(fileInput, { target: { files: [hugeFile] } });

      await waitFor(() => {
        expect(message.warning).toHaveBeenCalledWith('文件大小不能超过 10MB');
      });
      expect(mockGetUploadCredential).not.toHaveBeenCalled();
    });

    it('应允许自定义 maxSize 校验并显示正确的动态提示', async () => {
      const customMaxSize = 5 * 1024 * 1024; // 5MB
      render(<ImageUploadField bizType="IP_SERIES" maxSize={customMaxSize} />);

      const fileInput = document.querySelector('.ant-upload input[type="file"]') as HTMLInputElement;
      const file = new File(['x'.repeat(6 * 1024 * 1024)], 'big.png', { type: 'image/png' });
      fireEvent.change(fileInput, { target: { files: [file] } });

      await waitFor(() => {
        expect(message.warning).toHaveBeenCalledWith('文件大小不能超过 5MB');
      });
    });
    it('应允许自定义 acceptTypes 校验并显示对应格式提示', async () => {
      render(<ImageUploadField bizType="IP_SERIES" acceptTypes={['image/png']} />);

      const fileInput = document.querySelector('.ant-upload input[type="file"]') as HTMLInputElement;
      const invalidFile = new File(['content'], 'test.jpg', { type: 'image/jpeg' });
      fireEvent.change(fileInput, { target: { files: [invalidFile] } });

      await waitFor(() => {
        expect(message.warning).toHaveBeenCalledWith('仅支持 PNG 格式');
      });
      expect(mockGetUploadCredential).not.toHaveBeenCalled();
    });
  });


  describe('上传成功', () => {
    it('上传成功后应回调 onChange 并传入完整 URL', async () => {
      const onChange = jest.fn();
      mockUploadFile.mockResolvedValue('http://file-service/api/files/view/new-file.png');

      render(<ImageUploadField bizType="CATEGORY_ICON" onChange={onChange} />);

      const fileInput = document.querySelector('.ant-upload input[type="file"]') as HTMLInputElement;
      const validFile = new File(['image-content'], 'test.png', { type: 'image/png' });
      fireEvent.change(fileInput, { target: { files: [validFile] } });

      await waitFor(() => {
        expect(onChange).toHaveBeenCalledWith('http://file-service/api/files/view/new-file.png');
      });
    });

    it('应使用正确的 bizType 获取上传凭证', async () => {
      const onChange = jest.fn();
      mockGetUploadCredential.mockResolvedValue({
        token: 'token-cat',
        uploadUrl: 'http://file-service/api/files/upload',
      });
      mockUploadFile.mockResolvedValue('http://file-service/api/files/view/cat-icon.png');

      render(<ImageUploadField bizType="CATEGORY_ICON" onChange={onChange} />);

      const fileInput = document.querySelector('.ant-upload input[type="file"]') as HTMLInputElement;
      const validFile = new File(['img'], 'icon.png', { type: 'image/png' });
      fireEvent.change(fileInput, { target: { files: [validFile] } });

      await waitFor(() => {
        expect(mockGetUploadCredential).toHaveBeenCalledWith('CATEGORY_ICON');
      });
    });
  });

  describe('上传失败', () => {
    it('上传失败应显示错误信息且不回调 onChange', async () => {
      const onChange = jest.fn();
      mockUploadFile.mockRejectedValue(new Error('网络错误，上传失败'));

      render(<ImageUploadField bizType="CATEGORY_ICON" onChange={onChange} />);

      const fileInput = document.querySelector('.ant-upload input[type="file"]') as HTMLInputElement;
      const validFile = new File(['image-content'], 'test.png', { type: 'image/png' });
      fireEvent.change(fileInput, { target: { files: [validFile] } });

      await waitFor(() => {
        expect(message.error).toHaveBeenCalledWith('网络错误，上传失败');
      });
      expect(onChange).not.toHaveBeenCalled();
    });
  });

  describe('用户未登录', () => {
    it('getUserInfo 返回 null 时应显示错误信息', async () => {
      const onChange = jest.fn();
      mockGetUserInfo.mockReturnValue(null);

      render(<ImageUploadField bizType="CATEGORY_ICON" onChange={onChange} />);

      const fileInput = document.querySelector('.ant-upload input[type="file"]') as HTMLInputElement;
      const validFile = new File(['image-content'], 'test.png', { type: 'image/png' });
      fireEvent.change(fileInput, { target: { files: [validFile] } });

      await waitFor(() => {
        expect(message.error).toHaveBeenCalledWith('用户信息获取失败，请重新登录');
      });
      expect(onChange).not.toHaveBeenCalled();
    });
  });

  describe('删除', () => {
    it('allowRemove=true 且有 value 时应显示删除图标', () => {
      render(
        <ImageUploadField
          bizType="IP_SERIES"
          value={1}
          allowRemove
        />,
      );
      const actionBtn = document.querySelector('.ant-upload-list-item-action');
      expect(actionBtn).toBeInTheDocument();
    });

    it('allowRemove=false 时不显示删除图标', () => {
      render(
        <ImageUploadField
          bizType="CARD_TEMPLATE"
          value={2}
          allowRemove={false}
        />,
      );
      const actionBtn = document.querySelector('.ant-upload-list-item-action');
      expect(actionBtn).toBeNull();
    });
  });

  describe('预览', () => {
    it('preview=true 且有 value 时点击缩略图触发预览', async () => {
      render(
        <ImageUploadField
          bizType="IP_SERIES"
          value={1}
          preview
        />,
      );

      const thumbnail = document.querySelector('.ant-upload-list-item-thumbnail') as HTMLElement;
      expect(thumbnail).toBeInTheDocument();

      fireEvent.click(thumbnail);

      await waitFor(() => {
        // Image 组件的 preview mask 会渲染
        const previewMask = document.querySelector('.ant-image-preview-mask');
        expect(previewMask).toBeInTheDocument();
      });
    });

    it('preview=false 时不触发预览', () => {
      const { container } = render(
        <ImageUploadField
          bizType="IP_SERIES"
          value={1}
          preview={false}
        />,
      );
      // preview=false 时不应渲染隐藏的 Image 组件
      const hiddenImages = container.querySelectorAll('.ant-image');
      expect(hiddenImages.length).toBe(0);
    });
  });
});

import React, { useState } from 'react';
import { Image, message, Upload } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { UploadFile, UploadProps } from 'antd/es/upload/interface';

import { getUploadCredential, uploadFile } from '@/services/fileUpload';
import { getUserInfo } from '@/utils/token';

/** MIME 类型到可读后缀名的映射 */
const MIME_TO_SUFFIX: Record<string, string> = {
  'image/jpeg': 'JPG',
  'image/png': 'PNG',
  'image/gif': 'GIF',
  'image/webp': 'WebP',
  'image/bmp': 'BMP',
  'image/svg+xml': 'SVG',
};

/** 将 MIME 类型转为可读的后缀名 */
const mimeToSuffix = (mime: string): string => MIME_TO_SUFFIX[mime] || mime;

/** 图片上传组件 Props */
export interface ImageUploadFieldProps {
  /** 业务类型，对应后端 bizType（如 'CATEGORY_ICON'） */
  bizType: string;
  /** 上传占位区域显示的文字，默认「上传图片」 */
  placeholder?: string;
  /** 是否允许预览（点击缩略图弹出大图），默认 true */
  preview?: boolean;
  /** 是否允许删除（渲染 removeIcon），默认 true */
  allowRemove?: boolean;
  /** 受控值（完整图片 URL） */
  value?: string;
  /** 值变更回调 */
  onChange?: (value: string | undefined) => void;
  /** 文件大小上限（字节），默认 10MB */
  maxSize?: number;
  /** 允许的文件类型，默认 ['image/jpeg','image/png','image/gif','image/webp'] */
  acceptTypes?: string[];
}

/** 通用图片上传组件（凭证式上传） */
const ImageUploadField: React.FC<ImageUploadFieldProps> = ({
  bizType,
  placeholder = '上传图片',
  preview = true,
  allowRemove = true,
  value,
  onChange,
  maxSize = 10 * 1024 * 1024,
  acceptTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'],
}) => {
  const [uploading, setUploading] = useState(false);
  /** 图片预览状态 */
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewImage, setPreviewImage] = useState('');

  const fileList: UploadFile[] = value
    ? [{ uid: '-1', name: 'image', status: 'done', url: value }]
    : [];

  /** 上传前校验文件类型和大小 */
  const handleBeforeUpload = (file: File) => {
    if (!acceptTypes.includes(file.type)) {
      const suffixes = acceptTypes.map(mimeToSuffix).join('、');
      message.warning(`仅支持 ${suffixes} 格式`);
      return false;
    }
    if (file.size > maxSize) {
      const sizeMB = Math.round((maxSize / 1024 / 1024) * 100) / 100;
      message.warning(`文件大小不能超过 ${sizeMB}MB`);
      return false;
    }
    return true;
  };

  /** 凭证式上传：获取凭证 → 直传文件服务 → 拼接完整 URL */
  const handleCustomRequest: UploadProps['customRequest'] = async (options) => {
    const { file, onSuccess, onError } = options;
    setUploading(true);
    try {
      const credential = await getUploadCredential(bizType);
      const userInfo = getUserInfo();
      if (!userInfo) {
        throw new Error('用户信息获取失败，请重新登录');
      }
      const fullUrl = await uploadFile(
        credential.token,
        credential.uploadUrl,
        file as File,
        userInfo.id,
      );
      onChange?.(fullUrl);
      onSuccess?.({ url: fullUrl });
    } catch (e: any) {
      onError?.(e);
      message.error(e.message || '上传失败');
    } finally {
      setUploading(false);
    }
  };

  /** 删除已上传的图片 */
  const handleRemove = () => {
    onChange?.(undefined);
    return true;
  };

  /** 点击缩略图时使用 Image 组件预览，而非新窗口打开 */
  const handlePreview = (file: UploadFile) => {
    setPreviewImage(file.url || '');
    setPreviewOpen(true);
  };

  return (
    <>
      <Upload
        accept={acceptTypes.join(',')}
        maxCount={1}
        listType="picture-card"
        fileList={fileList}
        showUploadList={{ showRemoveIcon: allowRemove }}
        beforeUpload={handleBeforeUpload}
        customRequest={handleCustomRequest}
        onRemove={handleRemove}
        onPreview={preview ? handlePreview : undefined}
      >
        {!value && !uploading && (
          <div>
            <PlusOutlined style={{ fontSize: 20 }} />
            <div style={{ marginTop: 8 }}>{placeholder}</div>
          </div>
        )}
      </Upload>
      {preview && (
        <Image
          style={{ display: 'none' }}
          src={previewImage}
          preview={{
            visible: previewOpen,
            onVisibleChange: (visible) => setPreviewOpen(visible),
          }}
        />
      )}
    </>
  );
};

export default ImageUploadField;

import React, { useState } from 'react';
import { Image, message, Upload } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { UploadFile, UploadProps } from 'antd/es/upload/interface';

import { getUploadCredential, uploadFile } from '@/services/fileUpload';
import { getUserInfo } from '@/utils/token';

const MIME_TO_SUFFIX: Record<string, string> = {
  'image/jpeg': 'JPG',
  'image/png': 'PNG',
  'image/gif': 'GIF',
  'image/webp': 'WebP',
  'image/bmp': 'BMP',
  'image/svg+xml': 'SVG',
};

const mimeToSuffix = (mime: string): string => MIME_TO_SUFFIX[mime] || mime;

export interface ImageUploadFieldProps {
  bizType: string;
  placeholder?: string;
  preview?: boolean;
  allowRemove?: boolean;
  /** 受控值（fileId，number 类型） */
  value?: number;
  /** fileId 变更回调 */
  onChange?: (fileId: number | undefined) => void;
  /** 当前 fileId 对应的 url，编辑模式由父组件传入用于回填缩略图 */
  url?: string;
  maxSize?: number;
  acceptTypes?: string[];
}

const ImageUploadField: React.FC<ImageUploadFieldProps> = ({
  bizType,
  placeholder = '上传图片',
  preview = true,
  allowRemove = true,
  value,
  onChange,
  url,
  maxSize = 10 * 1024 * 1024,
  acceptTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'],
}) => {
  const [uploading, setUploading] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewImage, setPreviewImage] = useState('');
  const [internalUrl, setInternalUrl] = useState<string | undefined>(undefined);
  const [removed, setRemoved] = useState(false);

  const displayUrl = removed ? undefined : internalUrl ?? url;

  const fileList: UploadFile[] = displayUrl
    ? [{ uid: '-1', name: 'image', status: 'done', url: displayUrl }]
    : [];

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

  const handleCustomRequest: UploadProps['customRequest'] = async (options) => {
    const { file, onSuccess, onError } = options;
    setUploading(true);
    try {
      const credential = await getUploadCredential(bizType);
      const userInfo = getUserInfo();
      if (!userInfo) {
        throw new Error('用户信息获取失败，请重新登录');
      }
      const result = await uploadFile(
        credential.token,
        credential.uploadUrl,
        file as File,
        userInfo.id,
      );
      setInternalUrl(result.url);
      setRemoved(false);
      onChange?.(result.fileId);
      onSuccess?.({ url: result.url });
    } catch (e: any) {
      onError?.(e);
      message.error(e.message || '上传失败');
    } finally {
      setUploading(false);
    }
  };

  const handleRemove = () => {
    setInternalUrl(undefined);
    setRemoved(true);
    onChange?.(undefined);
    return true;
  };

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
        {!displayUrl && !uploading && (
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

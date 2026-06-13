import React, { useState } from 'react';
import { Image, message, Upload } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { UploadFile, UploadProps } from 'antd/es/upload/interface';

import { getUploadCredential, uploadFile } from '@/services/fileUpload';
import { getUserInfo } from '@/utils/token';

/** 星标符号映射 */
const STAR_LABELS: Record<number, string> = {
  1: '★1',
  2: '★2',
  3: '★3',
  4: '★4',
  5: '★5',
};

/** 星级图片上传组件 */
const StarImageUpload: React.FC<{
  starLevel: number;
  value?: string;
  onChange?: (url: string | undefined) => void;
}> = ({ starLevel, value, onChange }) => {
  const [uploading, setUploading] = useState(false);
  /** 图片预览状态 */
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewImage, setPreviewImage] = useState('');

  const fileList: UploadFile[] = value
    ? [{ uid: '-1', name: `star-${starLevel}`, status: 'done', url: value }]
    : [];

  /** 上传前校验文件类型和大小 */
  const handleBeforeUpload = (file: File) => {
    const validTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
    if (!validTypes.includes(file.type)) {
      message.warning('仅支持 JPG、PNG、GIF、WebP 格式');
      return false;
    }
    if (file.size > 10 * 1024 * 1024) {
      message.warning('文件大小不能超过 10MB');
      return false;
    }
    return true;
  };

  /** 凭证式上传：获取凭证 → 直传文件服务 → 拼接完整 URL */
  const handleCustomRequest: UploadProps['customRequest'] = async (options) => {
    const { file, onSuccess, onError } = options;
    setUploading(true);
    try {
      const credential = await getUploadCredential('CARD_TEMPLATE');
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

  /** 点击缩略图时使用 Image 组件预览，而非新窗口打开 */
  const handlePreview = (file: UploadFile) => {
    setPreviewImage(file.url || '');
    setPreviewOpen(true);
  };

  return (
    <>
      <Upload
        accept="image/jpeg,image/png,image/gif,image/webp"
        maxCount={1}
        listType="picture-card"
        fileList={fileList}
        showUploadList={{ showRemoveIcon: false }}
        beforeUpload={handleBeforeUpload}
        customRequest={handleCustomRequest}
        onPreview={handlePreview}
      >
        {!value && !uploading && (
          <div>
            <PlusOutlined style={{ fontSize: 20 }} />
            <div style={{ marginTop: 8 }}>{STAR_LABELS[starLevel] || `★${starLevel}`}</div>
          </div>
        )}
      </Upload>
      <Image
        style={{ display: 'none' }}
        src={previewImage}
        preview={{
          visible: previewOpen,
          onVisibleChange: (visible) => setPreviewOpen(visible),
        }}
      />
    </>
  );
};

export default StarImageUpload;

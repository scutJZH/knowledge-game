import { useState, useRef, useEffect } from 'react';
import { Spin, message } from 'antd';
import { getUploadCredential } from '@/services/group-api';

interface AvatarUploadProps {
  value?: number;
  onChange?: (fileId: number) => void;
}

function AvatarUpload({ value, onChange }: AvatarUploadProps) {
  const [uploading, setUploading] = useState(false);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // 根据 fileId 加载已有头像 URL（编辑场景）
  useEffect(() => {
    if (value != null && previewUrl == null) {
      setPreviewUrl(`/api/file/${value}?action=download`);
    }
  }, [value]);

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setUploading(true);
    try {
      const cred = await getUploadCredential('STUDY_GROUP_AVATAR', 1);
      const { uploadUrl, fileId: newFileId } = cred[0];
      const formData = new FormData();
      formData.append('file', file);
      await fetch(uploadUrl, { method: 'POST', body: formData });
      setPreviewUrl(URL.createObjectURL(file));
      onChange?.(newFileId);
    } catch {
      message.error('上传失败，请重试');
    } finally {
      setUploading(false);
      if (inputRef.current) inputRef.current.value = '';
    }
  };

  return (
    <div
      className="avatar-upload"
      onClick={() => inputRef.current?.click()}
      style={{ cursor: 'pointer', position: 'relative' }}
    >
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        style={{ display: 'none' }}
        onChange={handleFileChange}
      />
      {uploading ? (
        <Spin />
      ) : previewUrl ? (
        <img
          src={previewUrl}
          alt="头像预览"
          style={{ width: 60, height: 60, borderRadius: 12, objectFit: 'cover' }}
        />
      ) : (
        <div
          style={{
            width: 60, height: 60, borderRadius: 12, border: '2px dashed #d9d9d9',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: '#999', fontSize: 12,
          }}
        >
          点击上传头像
        </div>
      )}
    </div>
  );
}

export default AvatarUpload;

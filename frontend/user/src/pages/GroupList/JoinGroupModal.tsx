import { useState } from 'react';
import { Modal, Input, message } from 'antd';
import { joinByInvite } from '@/services/group-api';
import { ApiError } from '@/types/api';

interface JoinGroupModalProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

function JoinGroupModal({ open, onClose, onSuccess }: JoinGroupModalProps) {
  const [code, setCode] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setCode(e.target.value.toUpperCase());
  };

  const handleSubmit = async () => {
    if (code.length !== 8) return;
    setSubmitting(true);
    try {
      await joinByInvite(code);
      message.success('加入成功');
      setCode('');
      onClose();
      onSuccess();
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : '邀请码无效';
      message.error(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancel = () => {
    setCode('');
    onClose();
  };

  return (
    <Modal
      title="加入群组"
      open={open}
      onOk={handleSubmit}
      onCancel={handleCancel}
      confirmLoading={submitting}
      okText="加入"
      cancelText="取消"
      okButtonProps={{ disabled: code.length !== 8 }}
      destroyOnClose
      getContainer={false}
    >
      <div style={{ textAlign: 'center' }}>
        <div style={{ fontSize: 13, color: '#999', marginBottom: 12 }}>输入群主分享的邀请码</div>
        <Input
          placeholder="8 位邀请码"
          value={code}
          onChange={handleChange}
          maxLength={8}
          style={{ fontSize: 20, textAlign: 'center', letterSpacing: 6, fontFamily: 'monospace' }}
        />
        <div style={{ fontSize: 11, color: '#bbb', marginTop: 4 }}>自动转大写</div>
      </div>
    </Modal>
  );
}

export default JoinGroupModal;

import { useState } from 'react';
import { Modal, Input, Segmented, Form, message } from 'antd';
import { createGroup } from '@/services/group-api';
import AvatarUpload from './AvatarUpload';

interface CreateGroupModalProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

function CreateGroupModal({ open, onClose, onSuccess }: CreateGroupModalProps) {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [avatarFileId, setAvatarFileId] = useState<number | undefined>();

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      await createGroup({
        name: values.name,
        description: values.description || undefined,
        avatarFileId,
        joinPolicy: values.joinPolicy || 'OPEN',
      });
      message.success('群组创建成功');
      form.resetFields();
      setAvatarFileId(undefined);
      onClose();
      onSuccess();
    } catch {
      if (submitting) message.error('创建失败，请重试');
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancel = () => {
    form.resetFields();
    setAvatarFileId(undefined);
    onClose();
  };

  return (
    <Modal
      title="创建群组"
      open={open}
      onOk={handleSubmit}
      onCancel={handleCancel}
      confirmLoading={submitting}
      okText="创建"
      cancelText="取消"
      destroyOnClose
    >
      <Form form={form} layout="vertical" initialValues={{ joinPolicy: 'OPEN' }}>
        <Form.Item
          name="name"
          label="群组名称"
          rules={[{ required: true, message: '请输入群组名称' }, { max: 50, message: '最多 50 字' }]}
        >
          <Input placeholder="给群组起个名字" maxLength={50} />
        </Form.Item>
        <Form.Item name="description" label="描述" rules={[{ max: 500, message: '最多 500 字' }]}>
          <Input.TextArea placeholder="介绍一下这个群组…" maxLength={500} rows={3} />
        </Form.Item>
        <Form.Item label="头像">
          <AvatarUpload value={avatarFileId} onChange={setAvatarFileId} />
        </Form.Item>
        <Form.Item name="joinPolicy" label="加入方式">
          <Segmented options={[{ value: 'OPEN', label: '🌐 公开加入' }, { value: 'INVITE_ONLY', label: '🔒 邀请加入' }]} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

export default CreateGroupModal;

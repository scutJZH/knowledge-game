import { useState } from 'react';
import { Modal, Form, Input, Radio, message } from 'antd';
import type { StudyGroupDetailResponse } from '@/types/group';
import { updateGroup } from '@/services/group-api';
import AvatarUpload from '@/pages/GroupList/AvatarUpload';

interface Props {
  open: boolean;
  group: StudyGroupDetailResponse;
  onClose: () => void;
  onUpdated: () => void;
}

export default function EditGroupModal({ open, group, onClose, onUpdated }: Props) {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const handleOk = async () => {
    const values = await form.validateFields();
    setLoading(true);
    try {
      await updateGroup(group.id, values);
      message.success('已更新');
      onUpdated();
      onClose();
    } catch { message.error('更新失败'); }
      finally { setLoading(false); }
  };

  return (
    <Modal title="编辑群组信息" open={open} onOk={handleOk} onCancel={onClose} confirmLoading={loading}>
      <Form form={form} layout="vertical" initialValues={{
        name: group.name,
        description: group.description,
        avatarFileId: group.avatarFileId,
        joinPolicy: group.joinPolicy,
      }}>
        <Form.Item name="name" label="群组名称" rules={[{ required: true, message: '请输入名称' }, { max: 50 }]}>
          <Input maxLength={50} />
        </Form.Item>
        <Form.Item name="avatarFileId" label="头像">
          <AvatarUpload />
        </Form.Item>
        <Form.Item name="description" label="描述" rules={[{ max: 500 }]}>
          <Input.TextArea maxLength={500} rows={3} />
        </Form.Item>
        <Form.Item name="joinPolicy" label="加入方式">
          <Radio.Group>
            <Radio value="OPEN">公开加入</Radio>
            <Radio value="INVITE_ONLY">仅邀请</Radio>
          </Radio.Group>
        </Form.Item>
      </Form>
    </Modal>
  );
}

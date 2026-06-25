import { Modal, Descriptions, Tag } from 'antd';
import type { GroupMemberListResponse } from '@/types/group';
import dayjs from 'dayjs';

interface Props {
  open: boolean;
  member: GroupMemberListResponse;
  onClose: () => void;
}

export default function MemberDetailModal({ open, member, onClose }: Props) {
  const roleColor = member.role === 'OWNER' ? 'purple' : member.role === 'ADMIN' ? 'blue' : 'default';

  return (
    <Modal title="成员详情" open={open} onCancel={onClose} footer={null}>
      <Descriptions column={1} size="small">
        <Descriptions.Item label="昵称">{member.nickname}</Descriptions.Item>
        <Descriptions.Item label="角色">
          <Tag color={roleColor}>{member.role}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="累计积分">{member.points}</Descriptions.Item>
        <Descriptions.Item label="加入时间">
          {dayjs(member.joinedAt).format('YYYY-MM-DD HH:mm:ss')}
        </Descriptions.Item>
      </Descriptions>
    </Modal>
  );
}

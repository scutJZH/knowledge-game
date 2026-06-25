import { Modal, Descriptions, Tag } from 'antd';
import type { GroupMemberListResponse } from '@/types/group';
import dayjs from 'dayjs';

interface Props {
  open: boolean;
  member: GroupMemberListResponse;
  onClose: () => void;
}

const ROLE_LABELS: Record<string, string> = { OWNER: '群主', ADMIN: '管理员', MEMBER: '成员' };
const ROLE_COLORS: Record<string, string> = { OWNER: 'purple', ADMIN: 'blue', MEMBER: 'default' };

export default function MemberDetailModal({ open, member, onClose }: Props) {
  return (
    <Modal title="成员详情" open={open} onCancel={onClose} footer={null}>
      <Descriptions column={1} size="small">
        <Descriptions.Item label="昵称">{member.nickname}</Descriptions.Item>
        <Descriptions.Item label="角色">
          <Tag color={ROLE_COLORS[member.role] || 'default'}>{ROLE_LABELS[member.role] || member.role}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="累计积分">{member.points}</Descriptions.Item>
        <Descriptions.Item label="加入时间">
          {dayjs(member.joinedAt).format('YYYY-MM-DD HH:mm:ss')}
        </Descriptions.Item>
      </Descriptions>
    </Modal>
  );
}

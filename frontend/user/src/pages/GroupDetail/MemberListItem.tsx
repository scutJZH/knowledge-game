import { useState } from 'react';
import { Dropdown, Modal, message, Tag } from 'antd';
import type { GroupMemberListResponse } from '@/types/group';
import { kickMember, updateMemberRole, transferOwnership } from '@/services/group-api';
import { useAuthStore } from '@/store/auth-store';
import MemberDetailModal from './MemberDetailModal';

const ROLE_LABELS: Record<string, string> = { OWNER: '群主', ADMIN: '管理员', MEMBER: '成员' };
const ROLE_COLORS: Record<string, string> = { OWNER: 'purple', ADMIN: 'blue', MEMBER: 'default' };

interface Props {
  member: GroupMemberListResponse;
  groupId: number;
  rank: number;
  medal: string | undefined;
  myRole: string;
  onActionDone: () => void;
}

export default function MemberListItem({ member, groupId, rank, medal, myRole, onActionDone }: Props) {
  const [detailOpen, setDetailOpen] = useState(false);
  const currentUserId = useAuthStore.getState().user?.id;
  const isMe = currentUserId === member.userId;

  const handleKick = () => {
    Modal.confirm({
      title: '踢出成员',
      content: `确定将 ${member.nickname} 踢出群组？`,
      okText: '确定',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await kickMember(groupId, member.userId);
          message.success('已踢出');
          onActionDone();
        } catch { message.error('操作失败'); }
      },
    });
  };

  const handlePromote = async () => {
    try {
      await updateMemberRole(groupId, member.userId, 'ADMIN');
      message.success('已提升为管理员');
      onActionDone();
    } catch { message.error('操作失败'); }
  };

  const handleDemote = async () => {
    try {
      await updateMemberRole(groupId, member.userId, 'MEMBER');
      message.success('已降级为成员');
      onActionDone();
    } catch { message.error('操作失败'); }
  };

  const handleTransfer = () => {
    Modal.confirm({
      title: '转让群主',
      content: `确定将群主转让给 ${member.nickname}？`,
      okText: '确定转让',
      onOk: async () => {
        try {
          await transferOwnership(groupId, member.userId);
          message.success('已转让');
          onActionDone();
        } catch { message.error('操作失败'); }
      },
    });
  };

  const menuItems: Array<{ key: string; label: string; danger?: boolean; onClick: () => void }> = [];
  menuItems.push({ key: 'detail', label: '查看详情', onClick: () => setDetailOpen(true) });

  if (!isMe) {
    if (myRole === 'OWNER') {
      if (member.role === 'MEMBER') {
        menuItems.push({ key: 'promote', label: '提升为管理员', onClick: handlePromote });
      }
      if (member.role === 'ADMIN') {
        menuItems.push({ key: 'demote', label: '降级为成员', onClick: handleDemote });
      }
      if (member.role !== 'OWNER') {
        menuItems.push({ key: 'transfer', label: '转让群主', onClick: handleTransfer });
      }
    }
    if (myRole === 'OWNER' || (myRole === 'ADMIN' && member.role === 'MEMBER')) {
      if (member.role !== 'OWNER') {
        menuItems.push({ key: 'kick', label: '踢出群组', danger: true, onClick: handleKick });
      }
    }
  }

  return (
    <>
      <Dropdown menu={{ items: menuItems }} trigger={['click']}>
        <div style={{
          display: 'flex', alignItems: 'center', padding: '10px 0',
          borderBottom: '1px solid #f0f0f0', cursor: 'pointer',
        }}>
          {medal && <span style={{ fontSize: 20, marginRight: 8 }}>{medal}</span>}
          {!medal && <span style={{ width: 28, marginRight: 8, textAlign: 'center', color: '#bbb' }}>{rank + 1}</span>}
          <div style={{
            width: 40, height: 40, borderRadius: 20, background: '#f0f0f0',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontWeight: 'bold', color: '#666', marginRight: 12,
          }}>
            {member.nickname.charAt(0)}
          </div>
          <div style={{ flex: 1 }}>
            <div>{member.nickname}</div>
          </div>
          <Tag color={ROLE_COLORS[member.role]}>{ROLE_LABELS[member.role]}</Tag>
          <span style={{ marginLeft: 12, fontWeight: 'bold' }}>{member.points} 分</span>
        </div>
      </Dropdown>
      <MemberDetailModal open={detailOpen} member={member} onClose={() => setDetailOpen(false)} />
    </>
  );
}

import { useEffect, useState } from 'react';
import { Button, Space, Input, Select, message, Card, Popconfirm, Modal } from 'antd';
import { CopyOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import type { StudyGroupDetailResponse, GroupMemberListResponse } from '@/types/group';
import { regenerateInviteCode, disbandGroup, leaveGroup, listGroupMembers, transferOwnership } from '@/services/group-api';
import EditGroupModal from './EditGroupModal';

interface Props {
  group: StudyGroupDetailResponse;
  myRole: string;
  onUpdated: () => void;
}

export default function SettingsTab({ group, myRole, onUpdated }: Props) {
  const [editOpen, setEditOpen] = useState(false);
  const [inviteCode, setInviteCode] = useState(group.inviteCode || '');
  const [transferTarget, setTransferTarget] = useState<number | null>(null);
  const [members, setMembers] = useState<GroupMemberListResponse[]>([]);
  const navigate = useNavigate();

  useEffect(() => {
    listGroupMembers(group.id)
      .then((data) => setMembers((data as unknown as GroupMemberListResponse[]).filter(m => m.role !== 'OWNER')))
      .catch(() => {});
  }, [group.id]);

  const handleRegen = async () => {
    try {
      const res = await regenerateInviteCode(group.id) as unknown as { inviteCode: string };
      setInviteCode(res.inviteCode);
      message.success('邀请码已重新生成');
    } catch { message.error('重新生成失败'); }
  };

  const handleDisband = async (name: string) => {
    const inputEl = document.getElementById('disband-confirm-name') as HTMLInputElement | null;
    if (inputEl?.value !== name) { message.error('群组名称不匹配'); return; }
    try {
      await disbandGroup(group.id);
      message.success('群组已解散');
      navigate('/groups');
    } catch { message.error('解散失败'); }
  };

  const handleLeave = () => {
    Modal.confirm({
      title: '退出群组',
      content: '退出后您的积分将被清零且不可恢复，确定退出？',
      okText: '确定退出',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await leaveGroup(group.id);
          message.success('已退出群组');
          navigate('/groups');
        } catch { message.error('退出失败'); }
      },
    });
  };

  return (
    <div>
      <Card title="群组信息" style={{ marginBottom: 16 }}>
        <div style={{ marginBottom: 8 }}>
          <span style={{ color: '#666' }}>名称：</span><span>{group.name}</span>
        </div>
        {group.description && (
          <div style={{ marginBottom: 8 }}>
            <span style={{ color: '#666' }}>描述：</span><span>{group.description}</span>
          </div>
        )}
        <div style={{ marginBottom: 8 }}>
          <span style={{ color: '#666' }}>加入方式：</span><span>{group.joinPolicy === 'OPEN' ? '公开加入' : '仅邀请'}</span>
        </div>
        {myRole === 'OWNER' && (
          <Button onClick={() => setEditOpen(true)} style={{ marginTop: 4 }}>编辑群组信息</Button>
        )}
        <EditGroupModal open={editOpen} group={group} onClose={() => setEditOpen(false)} onUpdated={onUpdated} />
      </Card>

      {(myRole === 'OWNER' || myRole === 'ADMIN') && (
        <Card title="邀请码管理" style={{ marginBottom: 16 }}>
          <Space>
            <Input value={inviteCode} readOnly
              style={{ fontFamily: 'monospace', fontSize: 16, letterSpacing: 4, width: 200 }} />
            <Button icon={<CopyOutlined />}
              onClick={() => { navigator.clipboard.writeText(inviteCode); message.success('已复制'); }} />
            <Popconfirm title="确定重新生成？旧码将立即失效" onConfirm={handleRegen}>
              <Button danger>重新生成</Button>
            </Popconfirm>
          </Space>
        </Card>
      )}

      {myRole !== 'OWNER' && (
        <Card title="退出群组" style={{ marginBottom: 16 }}>
          <p style={{ color: '#999' }}>退出后您的积分将被清零且不可恢复</p>
          <Button danger onClick={handleLeave}>退出群组</Button>
        </Card>
      )}

      {myRole === 'OWNER' && (
        <Card title="转让群主" style={{ marginBottom: 16 }}>
          <p style={{ color: '#999' }}>选择一位成员作为新群主，转让后你将变为管理员</p>
          <Space>
            <Select
              showSearch
              placeholder="搜索成员昵称"
              value={transferTarget}
              onChange={setTransferTarget}
              filterOption={(input, option) =>
                (option?.label as string ?? '').toLowerCase().includes(input.toLowerCase())
              }
              options={members.map(m => ({ label: m.nickname, value: m.userId }))}
              style={{ width: 200 }}
            />
            <Popconfirm
              title={`确定将群主转让给 ${members.find(m => m.userId === transferTarget)?.nickname ?? ''}？`}
              onConfirm={async () => {
                if (transferTarget == null) return;
                try {
                  await transferOwnership(group.id, transferTarget);
                  message.success('已转让');
                  onUpdated();
                } catch { message.error('转让失败'); }
              }}
              okText="确定转让"
            >
              <Button disabled={transferTarget == null}>转让</Button>
            </Popconfirm>
          </Space>
        </Card>
      )}

      {myRole === 'OWNER' && (
        <Card title="危险区域">
          <Popconfirm
            title="解散群组"
            description={
              <div>
                <p>此操作不可逆！请输入群组名称确认：</p>
                <Input id="disband-confirm-name" placeholder={group.name} style={{ marginTop: 8 }} />
              </div>
            }
            onConfirm={() => handleDisband(group.name)}
            okText="确认解散"
            okButtonProps={{ danger: true }}
          >
            <Button danger>解散群组</Button>
          </Popconfirm>
        </Card>
      )}
    </div>
  );
}

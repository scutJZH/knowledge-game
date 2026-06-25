import { useState } from 'react';
import { Button, Space, Input, message, Card, Popconfirm } from 'antd';
import { CopyOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import type { StudyGroupDetailResponse } from '@/types/group';
import { regenerateInviteCode, disbandGroup } from '@/services/group-api';
import EditGroupModal from './EditGroupModal';

interface Props {
  group: StudyGroupDetailResponse;
  myRole: string;
  onUpdated: () => void;
}

export default function SettingsTab({ group, myRole, onUpdated }: Props) {
  const [editOpen, setEditOpen] = useState(false);
  const navigate = useNavigate();

  const handleRegen = async () => {
    try {
      await regenerateInviteCode(group.id);
      message.success('邀请码已重新生成');
      onUpdated();
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

  return (
    <div>
      {myRole === 'OWNER' && (
        <Card title="群组信息" style={{ marginBottom: 16 }}>
          <Button onClick={() => setEditOpen(true)}>编辑群组信息</Button>
          <EditGroupModal open={editOpen} group={group} onClose={() => setEditOpen(false)} onUpdated={onUpdated} />
        </Card>
      )}

      <Card title="邀请码管理" style={{ marginBottom: 16 }}>
        <Space>
          <Input value={group.inviteCode || ''} readOnly
            style={{ fontFamily: 'monospace', fontSize: 16, letterSpacing: 4, width: 200 }} />
          <Button icon={<CopyOutlined />}
            onClick={() => { navigator.clipboard.writeText(group.inviteCode || ''); message.success('已复制'); }} />
          <Popconfirm title="确定重新生成？旧码将立即失效" onConfirm={handleRegen}>
            <Button danger>重新生成</Button>
          </Popconfirm>
        </Space>
      </Card>

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

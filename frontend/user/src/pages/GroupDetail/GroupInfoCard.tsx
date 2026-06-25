import { Card, Button, Space, Tag, Typography, message } from 'antd';
import { PlayCircleOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import type { StudyGroupDetailResponse } from '@/types/group';

const { Text, Title } = Typography;

const ROLE_COLOR_MAP: Record<string, string> = { OWNER: 'purple', ADMIN: 'blue', MEMBER: 'default' };
const ROLE_LABEL_MAP: Record<string, string> = { OWNER: '群主', ADMIN: '管理员', MEMBER: '成员' };

function AvatarBlock({ fileId, url, name }: { fileId: number | null; url: string | null; name: string }) {
  if (url) {
    return <img src={url} alt={name} style={{ width: 56, height: 56, borderRadius: 12, objectFit: 'cover' }} />;
  }
  return (
    <div style={{
      width: 56, height: 56, borderRadius: 12,
      background: 'linear-gradient(135deg, #8b5cf6, #a78bfa)',
      color: '#fff', fontSize: 24, fontWeight: 'bold',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
    }}>
      {name.charAt(0)}
    </div>
  );
}

interface Props {
  group: StudyGroupDetailResponse;
}

export default function GroupInfoCard({ group }: Props) {
  return (
    <Card style={{ marginBottom: 16 }}>
      <Space align="start" size={16}>
        <AvatarBlock fileId={group.avatarFileId} url={group.avatarUrl} name={group.name} />
        <div style={{ flex: 1 }}>
          <Space>
            <Title level={4} style={{ margin: 0 }}>{group.name}</Title>
            <Tag color={ROLE_COLOR_MAP[group.myRole]}>{ROLE_LABEL_MAP[group.myRole]}</Tag>
          </Space>
          {group.description && (
            <Text type="secondary" style={{ display: 'block', marginTop: 4 }}>{group.description}</Text>
          )}
          <Space size={12} style={{ marginTop: 8 }}>
            <Text type="secondary">{group.joinPolicy === 'OPEN' ? '公开加入' : '仅邀请'}</Text>
            <Text type="secondary">{group.memberCount} 名成员</Text>
            <Text type="secondary">{dayjs(group.createdAt).format('YYYY-MM-DD HH:mm')} 创建</Text>
          </Space>
        </div>
      </Space>
      <Button type="primary" icon={<PlayCircleOutlined />} size="large" block
        style={{ marginTop: 16, height: 44 }}
        onClick={() => message.info('游戏即将开放')}>
        开始游戏
      </Button>
    </Card>
  );
}

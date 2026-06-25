import { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Spin, Result, Button, Tabs } from 'antd';
import type { StudyGroupDetailResponse } from '@/types/group';
import { getGroupDetail } from '@/services/group-api';
import GroupInfoCard from './GroupInfoCard';
import MemberTab from './MemberTab';
import KnowledgeTab from './KnowledgeTab';
import SettingsTab from './SettingsTab';
import './GroupDetail.css';

export default function GroupDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [group, setGroup] = useState<StudyGroupDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchGroup = useCallback(() => {
    if (!id) return;
    setLoading(true);
    setError(null);
    getGroupDetail(Number(id))
      .then((data) => setGroup(data as unknown as StudyGroupDetailResponse))
      .catch((e: { response?: { data?: { message?: string } }; message?: string }) =>
        setError(e?.response?.data?.message || '加载失败'))
      .finally(() => setLoading(false));
  }, [id]);

  useEffect(() => { fetchGroup(); }, [fetchGroup]);

  if (loading) return <Spin style={{ display: 'block', margin: '80px auto' }} />;

  if (error || !group) {
    return (
      <Result
        status="error"
        title={error || '群组不存在'}
        extra={[
          <Button key="retry" onClick={fetchGroup}>重试</Button>,
          <Button key="back" onClick={() => navigate('/groups')} type="primary">返回群组列表</Button>,
        ]}
      />
    );
  }

  const tabItems = [
    { key: 'members', label: '成员', children: <MemberTab groupId={group.id} myRole={group.myRole} /> },
    { key: 'knowledge', label: '知识库', children: <KnowledgeTab /> },
  ];

  if (group.myRole === 'OWNER' || group.myRole === 'ADMIN') {
    tabItems.push({
      key: 'settings',
      label: '设置',
      children: <SettingsTab group={group} myRole={group.myRole} onUpdated={fetchGroup} />,
    });
  }

  return (
    <div className="group-detail">
      <GroupInfoCard group={group} onRefreshed={fetchGroup} />
      <Tabs defaultActiveKey="members" items={tabItems} />
    </div>
  );
}

import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Spin, Result, Button } from 'antd';
import { listMyGroups } from '@/services/group-api';
import type { StudyGroupListResponse } from '@/types/group';
import GroupCard from './GroupCard';
import CreateGroupModal from './CreateGroupModal';
import JoinGroupModal from './JoinGroupModal';
import './GroupList.css';

function GroupList() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [groups, setGroups] = useState<StudyGroupListResponse[]>([]);
  const [createOpen, setCreateOpen] = useState(false);
  const [joinOpen, setJoinOpen] = useState(false);

  const fetchGroups = useCallback(() => {
    setLoading(true);
    setError(false);
    listMyGroups()
      .then(setGroups)
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    fetchGroups();
  }, [fetchGroups]);

  const handleCardClick = (id: number) => {
    navigate(`/groups/${id}`);
  };

  if (loading) return <Spin style={{ display: 'block', marginTop: 80 }} />;

  if (error) {
    return (
      <Result
        status="error"
        title="加载失败"
        subTitle="请检查网络后重试"
        extra={<Button onClick={fetchGroups}>重试</Button>}
      />
    );
  }

  const bottomButtons = (
    <div className="group-actions">
      <button className="btn-create" onClick={() => setCreateOpen(true)}>+ 创建群组</button>
      <button className="btn-join" onClick={() => setJoinOpen(true)}>🔗 加入群组</button>
    </div>
  );

  if (groups.length === 0) {
    return (
      <div className="group-empty">
        <div className="empty-icon">🏠</div>
        <div className="empty-title">还没有加入群组</div>
        <div className="empty-desc">
          群组是知识游戏的起点<br />创建或加入一个群组开始吧
        </div>
        {bottomButtons}
      </div>
    );
  }

  return (
    <div className="group-list">
      <div className="group-header">
        <span className="header-title">我的群组</span>
        <span className="header-count">{groups.length} 个</span>
      </div>
      {groups.map((g) => (
        <GroupCard key={g.id} group={g} onClick={() => handleCardClick(g.id)} />
      ))}
      {bottomButtons}
      <CreateGroupModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onSuccess={fetchGroups}
      />
      <JoinGroupModal
        open={joinOpen}
        onClose={() => setJoinOpen(false)}
        onSuccess={fetchGroups}
      />
    </div>
  );
}

export default GroupList;

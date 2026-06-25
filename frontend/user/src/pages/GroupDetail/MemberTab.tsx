import { useEffect, useState, useCallback } from 'react';
import { Spin, Empty, message } from 'antd';
import type { GroupMemberListResponse } from '@/types/group';
import { listGroupMembers } from '@/services/group-api';
import MemberListItem from './MemberListItem';

interface Props {
  groupId: number;
  myRole: string;
}

export default function MemberTab({ groupId, myRole }: Props) {
  const [members, setMembers] = useState<GroupMemberListResponse[]>([]);
  const [loading, setLoading] = useState(true);

  const fetch = useCallback(() => {
    setLoading(true);
    listGroupMembers(groupId)
      .then((data) => setMembers(data as unknown as GroupMemberListResponse[]))
      .catch(() => message.error('加载成员列表失败'))
      .finally(() => setLoading(false));
  }, [groupId]);

  useEffect(() => { fetch(); }, [fetch]);

  if (loading) return <Spin style={{ display: 'block', margin: '24px auto' }} />;
  if (!members.length) return <Empty description="暂无成员" />;

  const medalMap: Record<number, string> = { 0: '🥇', 1: '🥈', 2: '🥉' };

  return (
    <div>
      {members.map((m, idx) => (
        <MemberListItem
          key={m.userId}
          member={m}
          groupId={groupId}
          rank={idx}
          medal={medalMap[idx]}
          myRole={myRole}
          onActionDone={fetch}
        />
      ))}
    </div>
  );
}

import type { StudyGroupListResponse } from '@/types/group';

const ROLE_CONFIG: Record<string, { label: string; className: string }> = {
  OWNER: { label: '群主', className: 'role-tag-owner' },
  ADMIN: { label: '管理员', className: 'role-tag-admin' },
  MEMBER: { label: '成员', className: 'role-tag-member' },
};

function GroupCard({ group, onClick }: { group: StudyGroupListResponse; onClick: () => void }) {
  const roleInfo = ROLE_CONFIG[group.myRole] || ROLE_CONFIG.MEMBER;
  const initial = group.name.charAt(0);

  return (
    <div className="group-card" onClick={onClick} data-testid="group-card">
      <div className="card-avatar">
        {group.avatarUrl ? (
          <img src={group.avatarUrl} alt="" className="avatar-img" />
        ) : (
          <div className="avatar-placeholder">{initial}</div>
        )}
      </div>
      <div className="card-body">
        <div className="card-name">{group.name}</div>
        <div className="card-meta">{group.memberCount} 成员 · {roleInfo.label}</div>
      </div>
      <div className="card-right">
        <span className={`role-tag ${roleInfo.className}`}>{group.myRole}</span>
        <span className="card-arrow">›</span>
      </div>
    </div>
  );
}

export default GroupCard;

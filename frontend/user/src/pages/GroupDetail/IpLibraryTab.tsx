import { useEffect, useState } from 'react';
import { Spin, Result, Button, Checkbox, message } from 'antd';
import type { ActiveIpSeriesResponse } from '@/types/group';
import {
  listGroupIpLibrary,
  updateGroupIpLibrary,
  listActiveIpSeries,
} from '@/services/group-api';
import './IpLibraryTab.css';

interface Props {
  groupId: number;
  myRole: 'OWNER' | 'ADMIN' | 'MEMBER';
}

export default function IpLibraryTab({ groupId, myRole }: Props) {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [ipList, setIpList] = useState<ActiveIpSeriesResponse[]>([]);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [initialSelectedIds, setInitialSelectedIds] = useState<Set<number>>(new Set());
  const [saving, setSaving] = useState(false);

  const canEdit = myRole === 'OWNER' || myRole === 'ADMIN';

  useEffect(() => {
    setLoading(true);
    setError(null);
    Promise.all([listGroupIpLibrary(groupId), listActiveIpSeries()])
      .then(([linked, active]) => {
        setIpList(active);
        const linkedIds = new Set(linked.map((item) => item.ipSeriesId));
        const activeIds = new Set(active.map((ip) => ip.id));
        const validIds = new Set([...linkedIds].filter(id => activeIds.has(id)));
        setSelectedIds(validIds);
        setInitialSelectedIds(new Set(validIds));
      })
      .catch((e: { response?: { data?: { message?: string } }; message?: string }) =>
        setError(e?.response?.data?.message || '加载失败'),
      )
      .finally(() => setLoading(false));
  }, [groupId]);

  const handleToggle = (id: number) => {
    if (!canEdit) return;
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const hasChanges = () => {
    if (selectedIds.size !== initialSelectedIds.size) return true;
    for (const id of selectedIds) {
      if (!initialSelectedIds.has(id)) return true;
    }
    return false;
  };

  const handleSave = async () => {
    if (saving) return;
    setSaving(true);
    try {
      const result = await updateGroupIpLibrary(groupId, Array.from(selectedIds));
      const newIds = new Set(result.map((item) => item.ipSeriesId));
      setSelectedIds(newIds);
      setInitialSelectedIds(newIds);
      message.success('保存成功');
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        '保存失败';
      message.error(msg);
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <Spin style={{ display: 'block', margin: '40px auto' }} />;

  if (error) {
    return (
      <Result
        status="error"
        title={error}
        extra={
          <Button onClick={() => window.location.reload()} type="primary">
            重试
          </Button>
        }
      />
    );
  }

  if (ipList.length === 0) {
    return <div className="ip-library-empty">系统中暂无可用 IP 系列</div>;
  }

  return (
    <div className="ip-library-tab">
      <div className="ip-library-toolbar">
        <span className="ip-library-count">
          已选 {selectedIds.size} / 共 {ipList.length} 项
        </span>
        {canEdit && (
          <Button
            type="primary"
            disabled={!hasChanges()}
            loading={saving}
            onClick={handleSave}
          >
            保存修改
          </Button>
        )}
      </div>
      <div className="ip-library-grid">
        {ipList.map((ip) => {
          const checked = selectedIds.has(ip.id);
          return (
            <div
              key={ip.id}
              className={`ip-card ${checked ? 'ip-card-checked' : ''}`}
              onClick={() => handleToggle(ip.id)}
              data-testid={`ip-card-${ip.id}`}
            >
              <Checkbox
                checked={checked}
                disabled={!canEdit}
                className="ip-card-checkbox"
                onClick={(e) => e.stopPropagation()}
                onChange={() => handleToggle(ip.id)}
              />
              <div className="ip-card-cover">
                {ip.coverImageUrl ? (
                  <img src={ip.coverImageUrl} alt="" className="ip-card-cover-img" />
                ) : (
                  <div className="avatar-placeholder">{ip.name.charAt(0)}</div>
                )}
              </div>
              <div className="ip-card-name">{ip.name}</div>
              <div className="ip-card-code">{ip.code}</div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

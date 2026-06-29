import { useEffect, useState, useMemo } from 'react';
import { Spin, Result, Button, Modal, Input, Checkbox, Dropdown, message } from 'antd';
import { MoreOutlined, PlusOutlined } from '@ant-design/icons';
import type { MenuProps } from 'antd';
import type { GroupIpLibraryResponse, ActiveIpSeriesResponse } from '@/types/group';
import {
  listGroupIpLibrary,
  updateGroupIpLibrary,
  listActiveIpSeries,
  updateGroupIpLibraryStatus,
} from '@/services/group-api';
import './IpLibraryTab.css';

interface Props {
  groupId: number;
  myRole: 'OWNER' | 'ADMIN' | 'MEMBER';
}

export default function IpLibraryTab({ groupId, myRole }: Props) {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [linkedList, setLinkedList] = useState<GroupIpLibraryResponse[]>([]);
  const [allActive, setAllActive] = useState<ActiveIpSeriesResponse[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [modalSearch, setModalSearch] = useState('');
  const [modalSelected, setModalSelected] = useState<Set<number>>(new Set());
  const [saving, setSaving] = useState(false);

  const canEdit = myRole === 'OWNER' || myRole === 'ADMIN';
  const linkedIds = useMemo(() => new Set(linkedList.map((item) => item.ipSeriesId)), [linkedList]);

  const loadData = () => {
    setLoading(true);
    setError(null);
    Promise.all([listGroupIpLibrary(groupId), listActiveIpSeries()])
      .then(([linked, active]) => {
        setLinkedList(linked);
        setAllActive(active);
      })
      .catch((e: { response?: { data?: { message?: string } }; message?: string }) =>
        setError(e?.response?.data?.message || '加载失败'),
      )
      .finally(() => setLoading(false));
  };

  useEffect(() => { loadData(); }, [groupId]);

  const availableIps = useMemo(() => {
    return allActive.filter((ip) => !linkedIds.has(ip.id));
  }, [allActive, linkedIds]);

  const filteredAvailable = useMemo(() => {
    if (!modalSearch.trim()) return availableIps;
    const kw = modalSearch.trim().toLowerCase();
    return availableIps.filter(
      (ip) => ip.name.toLowerCase().includes(kw) || ip.code.toLowerCase().includes(kw),
    );
  }, [availableIps, modalSearch]);

  const handleAdd = async () => {
    if (modalSelected.size === 0) return;
    setSaving(true);
    const newIds = [...linkedIds, ...modalSelected];
    try {
      const result = await updateGroupIpLibrary(groupId, newIds);
      setLinkedList(result);
      setModalOpen(false);
      setModalSearch('');
      setModalSelected(new Set());
      message.success('添加成功');
    } catch (e: unknown) {
      message.error(
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message || '添加失败',
      );
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (ipSeriesId: number) => {
    setSaving(true);
    const newIds = linkedList
      .filter((item) => item.ipSeriesId !== ipSeriesId)
      .map((item) => item.ipSeriesId);
    try {
      const result = await updateGroupIpLibrary(groupId, newIds);
      setLinkedList(result);
      message.success('已删除');
    } catch (e: unknown) {
      message.error(
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message || '删除失败',
      );
    } finally {
      setSaving(false);
    }
  };

  const handleToggleStatus = async (ipSeriesId: number, status: 'ACTIVE' | 'DISABLED') => {
    setSaving(true);
    try {
      const updated = await updateGroupIpLibraryStatus(groupId, ipSeriesId, status);
      setLinkedList((prev) =>
        prev.map((item) => (item.ipSeriesId === ipSeriesId ? updated : item)),
      );
      message.success(status === 'ACTIVE' ? '已恢复' : '已禁用');
    } catch (e: unknown) {
      message.error(
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message || '操作失败',
      );
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
        extra={<Button onClick={loadData} type="primary">重试</Button>}
      />
    );
  }

  return (
    <div className="ip-library-tab">
      <div className="ip-library-toolbar">
        <span className="ip-library-count">
          已关联 {linkedList.filter((i) => i.status === 'ACTIVE').length} 项
          {linkedList.some((i) => i.status === 'DISABLED') &&
            `（${linkedList.filter((i) => i.status === 'DISABLED').length} 项已禁用）`}
        </span>
        {canEdit && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
            添加 IP 系列
          </Button>
        )}
      </div>

      {linkedList.length === 0 ? (
        <div className="ip-library-empty">
          {canEdit ? '暂未关联 IP 系列，点击「添加 IP 系列」开始' : '暂未关联 IP 系列'}
        </div>
      ) : (
        <div className="ip-library-grid">
          {linkedList.map((item) => (
            <IpCard
              key={item.ipSeriesId}
              item={item}
              canEdit={canEdit}
              onDelete={() => handleDelete(item.ipSeriesId)}
              onToggleStatus={(status) => handleToggleStatus(item.ipSeriesId, status)}
            />
          ))}
        </div>
      )}

      <Modal
        title="添加 IP 系列"
        open={modalOpen}
        onCancel={() => { setModalOpen(false); setModalSearch(''); setModalSelected(new Set()); }}
        footer={[
          <Button key="cancel" onClick={() => { setModalOpen(false); setModalSearch(''); setModalSelected(new Set()); }}>取消</Button>,
          <Button key="submit" type="primary" loading={saving} disabled={modalSelected.size === 0} onClick={handleAdd}>确定</Button>,
        ]}
        destroyOnClose
      >
        <Input placeholder="搜索 IP 名称或编码" value={modalSearch} onChange={(e) => setModalSearch(e.target.value)} allowClear style={{ marginBottom: 12 }} />
        <div className="ip-library-modal-list">
          {filteredAvailable.length === 0 ? (
            <div className="ip-library-empty">无可用 IP 系列</div>
          ) : (
            filteredAvailable.map((ip) => (
              <div
                key={ip.id}
                className={`ip-library-modal-item ${modalSelected.has(ip.id) ? 'ip-library-modal-item-checked' : ''}`}
                onClick={() => setModalSelected((prev) => { const next = new Set(prev); next.has(ip.id) ? next.delete(ip.id) : next.add(ip.id); return next; })}
              >
                <Checkbox checked={modalSelected.has(ip.id)} />
                <div className="ip-library-modal-item-cover">
                  {ip.coverImageUrl ? <img src={ip.coverImageUrl} alt="" /> : <div className="avatar-placeholder">{ip.name.charAt(0)}</div>}
                </div>
                <div>
                  <div className="ip-library-modal-item-name">{ip.name}</div>
                  <div className="ip-library-modal-item-code">{ip.code}</div>
                </div>
              </div>
            ))
          )}
        </div>
      </Modal>
    </div>
  );
}

function IpCard({
  item,
  canEdit,
  onDelete,
  onToggleStatus,
}: {
  item: GroupIpLibraryResponse;
  canEdit: boolean;
  onDelete: () => void;
  onToggleStatus: (status: 'ACTIVE' | 'DISABLED') => void;
}) {
  const isDisabled = item.status === 'DISABLED';

  const menuItems: MenuProps['items'] = isDisabled
    ? [
        { key: 'restore', label: '恢复', onClick: () => onToggleStatus('ACTIVE') },
        { key: 'delete', label: '删除', danger: true, onClick: onDelete },
      ]
    : [
        { key: 'disable', label: '禁用', onClick: () => onToggleStatus('DISABLED') },
        { key: 'delete', label: '删除', danger: true, onClick: onDelete },
      ];

  return (
    <div className={`ip-card ${isDisabled ? 'ip-card-disabled' : ''}`} data-testid={`ip-card-${item.ipSeriesId}`}>
      {canEdit && (
        <Dropdown menu={{ items: menuItems }} trigger={['click']} placement="bottomRight">
          <Button
            type="text"
            size="small"
            icon={<MoreOutlined />}
            className="ip-card-menu-btn"
            onClick={(e) => e.stopPropagation()}
            data-testid={`ip-card-menu-${item.ipSeriesId}`}
          />
        </Dropdown>
      )}
      <div className="ip-card-cover">
        {item.coverImageUrl ? (
          <img src={item.coverImageUrl} alt="" className="ip-card-cover-img" />
        ) : (
          <div className="avatar-placeholder">{item.ipSeriesName.charAt(0)}</div>
        )}
      </div>
      <div className="ip-card-name">{item.ipSeriesName}</div>
      <div className="ip-card-code">{item.ipSeriesCode}</div>
      {isDisabled && <div className="ip-card-disabled-tag">已禁用</div>}
    </div>
  );
}

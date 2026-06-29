import { useEffect, useState, useMemo } from 'react';
import { Spin, Result, Button, Modal, Input, Checkbox, Dropdown, message } from 'antd';
import { MoreOutlined, PlusOutlined } from '@ant-design/icons';
import type { MenuProps } from 'antd';
import type { GroupIpLibraryResponse, ActiveIpSeriesResponse } from '@/types/group';
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

  // 可选 IP：已启用但未关联到本群组的
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

  // 添加：合并当前 ID + 弹窗选中 ID → PUT
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
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        '添加失败';
      message.error(msg);
    } finally {
      setSaving(false);
    }
  };

  // 移除（禁用/删除）：当前 ID 剔除目标 → PUT
  const handleRemove = async (ipSeriesId: number) => {
    setSaving(true);
    const newIds = linkedList
      .filter((item) => item.ipSeriesId !== ipSeriesId)
      .map((item) => item.ipSeriesId);
    try {
      const result = await updateGroupIpLibrary(groupId, newIds);
      setLinkedList(result);
      message.success('操作成功');
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        '操作失败';
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
          <Button onClick={loadData} type="primary">重试</Button>
        }
      />
    );
  }

  return (
    <div className="ip-library-tab">
      <div className="ip-library-toolbar">
        <span className="ip-library-count">已关联 {linkedList.length} 项</span>
        {canEdit && (
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setModalOpen(true)}
          >
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
              onRemove={() => handleRemove(item.ipSeriesId)}
            />
          ))}
        </div>
      )}

      <Modal
        title="添加 IP 系列"
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false);
          setModalSearch('');
          setModalSelected(new Set());
        }}
        footer={[
          <Button
            key="cancel"
            onClick={() => {
              setModalOpen(false);
              setModalSearch('');
              setModalSelected(new Set());
            }}
          >
            取消
          </Button>,
          <Button
            key="submit"
            type="primary"
            loading={saving}
            disabled={modalSelected.size === 0}
            onClick={handleAdd}
          >
            确定
          </Button>,
        ]}
        destroyOnClose
      >
        <Input
          placeholder="搜索 IP 名称或编码"
          value={modalSearch}
          onChange={(e) => setModalSearch(e.target.value)}
          allowClear
          style={{ marginBottom: 12 }}
        />
        <div className="ip-library-modal-list">
          {filteredAvailable.length === 0 ? (
            <div className="ip-library-empty">无可用 IP 系列</div>
          ) : (
            filteredAvailable.map((ip) => (
              <div
                key={ip.id}
                className={`ip-library-modal-item ${modalSelected.has(ip.id) ? 'ip-library-modal-item-checked' : ''}`}
                onClick={() =>
                  setModalSelected((prev) => {
                    const next = new Set(prev);
                    next.has(ip.id) ? next.delete(ip.id) : next.add(ip.id);
                    return next;
                  })
                }
              >
                <Checkbox checked={modalSelected.has(ip.id)} />
                <div className="ip-library-modal-item-cover">
                  {ip.coverImageUrl ? (
                    <img src={ip.coverImageUrl} alt="" />
                  ) : (
                    <div className="avatar-placeholder">{ip.name.charAt(0)}</div>
                  )}
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

/** 单个已关联 IP 卡片，右上角 ⋮ 操作菜单 */
function IpCard({
  item,
  canEdit,
  onRemove,
}: {
  item: GroupIpLibraryResponse;
  canEdit: boolean;
  onRemove: () => void;
}) {
  const menuItems: MenuProps['items'] = [
    { key: 'disable', label: '禁用', onClick: onRemove },
    // TODO 优化需求：有群组成员已收集卡片时，删除选项置灰或后端校验拦截
    { key: 'delete', label: '删除', danger: true, onClick: onRemove },
  ];

  return (
    <div className="ip-card" data-testid={`ip-card-${item.ipSeriesId}`}>
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
    </div>
  );
}

import { useCallback, useEffect, useRef, useState } from 'react';
import { Card, Segmented, message } from 'antd';
import type { SegmentedValue } from 'antd/es/segmented';
import RecycleBinTable from './components/RecycleBinTable';
import {
  fetchRecycleBinList,
  fetchSupportedTypes,
  restoreItem,
  batchRestoreItems,
  purgeItem,
  batchPurgeItems,
  type RecycleBinItem,
  type SupportedType,
  type RecycleBinListParams,
  type BatchPurgeResult,
} from '@/services/recycleBin';

const SORT_FIELD_MAP: Record<string, string> = {
  originalName: 'originalName',
  deletedAt: 'deletedAt',
};

const RecycleBinPage: React.FC = () => {
  const [types, setTypes] = useState<SupportedType[]>([]);
  const [selectedType, setSelectedType] = useState<string>('ALL');
  const [dataSource, setDataSource] = useState<RecycleBinItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20 });
  const [selectedRowKeys, setSelectedRowKeys] = useState<number[]>([]);
  const keywordRef = useRef<string>('');
  const sortRef = useRef<{ sort?: string; order?: 'asc' | 'desc' }>({});

  useEffect(() => {
    fetchSupportedTypes()
      .then((data) => setTypes(data || []))
      .catch(() => message.error('加载资源类型失败'));
  }, []);

  const loadData = useCallback(async (params: RecycleBinListParams) => {
    setLoading(true);
    try {
      const result = await fetchRecycleBinList(params);
      setDataSource(result?.content || []);
      setTotal(result?.totalElements || 0);
    } catch (e: any) {
      message.error(e?.message || '查询失败');
    } finally {
      setLoading(false);
    }
  }, []);

  const refresh = useCallback(() => {
    loadData({
      page: pagination.current - 1,
      size: pagination.pageSize,
      resourceType: selectedType === 'ALL' ? undefined : selectedType,
      keyword: keywordRef.current || undefined,
      sort: sortRef.current.sort,
      order: sortRef.current.order,
    });
  }, [pagination.current, pagination.pageSize, selectedType, loadData]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const handleSearch = (value: string) => {
    keywordRef.current = value;
    setPagination({ current: 1, pageSize: pagination.pageSize });
  };

  const handleSort = (field: string, order: 'ascend' | 'descend' | null) => {
    if (!order) {
      sortRef.current = {};
    } else {
      const mapped = SORT_FIELD_MAP[field];
      sortRef.current = mapped
        ? { sort: mapped, order: order === 'ascend' ? 'asc' : 'desc' }
        : {};
    }
    setPagination({ current: 1, pageSize: pagination.pageSize });
  };

  const handleTypeChange = (value: SegmentedValue) => {
    setSelectedType(value as string);
    setPagination({ current: 1, pageSize: pagination.pageSize });
  };

  const handleRestore = async (id: number) => {
    try {
      await restoreItem(id);
      message.success('恢复成功，已回到原列表（停用状态）');
      refresh();
    } catch (e: any) {
      message.error(e?.message || '恢复失败');
    }
  };

  const handleBatchRestore = async () => {
    const ids = selectedRowKeys;
    try {
      const result = await batchRestoreItems(ids);
      const { successIds, failures } = result;
      if (failures.length === 0) {
        message.success(`成功恢复 ${successIds.length} 条`);
      } else if (successIds.length > 0) {
        message.warning(`成功 ${successIds.length} 条，失败 ${failures.length} 条`);
      } else {
        message.error(failures[0]?.errorMessage || '批量恢复失败');
      }
      refresh();
      setSelectedRowKeys([]);
    } catch (e: any) {
      message.error(e?.message || '批量恢复失败');
    }
  };

  const handlePurge = async (id: number) => {
    try {
      await purgeItem(id);
      message.success('永久删除成功');
      refresh();
    } catch (e: any) {
      message.error(e?.message || '永久删除失败');
    }
  };

  const handleBatchPurge = async (ids: number[]): Promise<BatchPurgeResult> => {
    const result = await batchPurgeItems(ids);
    const { successIds, failures } = result;
    if (failures.length === 0) {
      message.success(`成功永久删除 ${successIds.length} 条`);
    } else if (successIds.length > 0) {
      message.warning(`成功 ${successIds.length} 条，失败 ${failures.length} 条`);
    } else {
      message.error(failures[0]?.errorMessage || '批量永久删除失败');
    }
    refresh();
    setSelectedRowKeys([]);
    return result;
  };

  const segmentedOptions: { value: string; label: string }[] = [
    { value: 'ALL', label: '全部' },
    ...types.map((t) => ({ value: t.type, label: t.displayName })),
  ];

  return (
    <Card
      title="回收站"
      styles={{ body: { paddingTop: 0 } }}
    >
      <div style={{ marginBottom: 16, paddingTop: 16 }}>
        <Segmented
          options={segmentedOptions}
          value={selectedType}
          onChange={handleTypeChange}
        />
      </div>
      <RecycleBinTable
        dataSource={dataSource}
        loading={loading}
        total={total}
        pagination={pagination}
        onPaginationChange={(page, pageSize) =>
          setPagination({ current: page, pageSize })
        }
        onSearch={handleSearch}
        onSort={handleSort}
        selectedRowKeys={selectedRowKeys}
        onSelectChange={setSelectedRowKeys}
        onRestore={handleRestore}
        onBatchRestore={handleBatchRestore}
        onPurge={handlePurge}
        onBatchPurge={handleBatchPurge}
      />
    </Card>
  );
};

export default RecycleBinPage;

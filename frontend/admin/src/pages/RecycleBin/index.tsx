import { useCallback, useEffect, useRef, useState } from 'react';
import { Card, Layout, message } from 'antd';
import ResourceTypeTree from './components/ResourceTypeTree';
import RecycleBinTable from './components/RecycleBinTable';
import {
  fetchRecycleBinList,
  fetchSupportedTypes,
  type RecycleBinItem,
  type SupportedType,
  type RecycleBinListParams,
} from '@/services/recycleBin';

const { Sider, Content } = Layout;

/** antd ProTable sorter → sortField mapping */
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
    // 取消排序时清空 sort 参数，让后端使用默认排序
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

  return (
    <Card title="回收站">
      <Layout style={{ background: '#fff' }}>
        <Sider width={200} style={{ background: '#fff', padding: '12px 0' }}>
          <ResourceTypeTree
            types={types}
            onSelect={(key) => {
              setSelectedType(key);
              setPagination({ current: 1, pageSize: pagination.pageSize });
            }}
          />
        </Sider>
        <Content style={{ padding: '0 16px' }}>
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
          />
        </Content>
      </Layout>
    </Card>
  );
};

export default RecycleBinPage;

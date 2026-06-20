import { ProColumns, ProTable } from '@ant-design/pro-components';
import { Button, Input, Popconfirm, Space, Tag, Tooltip } from 'antd';
import type { RecycleBinItem } from '@/services/recycleBin';

interface RecycleBinTableProps {
  dataSource: RecycleBinItem[];
  loading: boolean;
  total: number;
  pagination: { current: number; pageSize: number };
  onPaginationChange: (page: number, pageSize: number) => void;
  onSearch: (keyword: string) => void;
  onSort: (sort: string, order: 'ascend' | 'descend' | null) => void;
  selectedRowKeys: number[];
  onSelectChange: (keys: number[]) => void;
  onRestore: (id: number) => void;
  onBatchRestore: () => void;
}

const RecycleBinTable: React.FC<RecycleBinTableProps> = ({
  dataSource,
  loading,
  total,
  pagination,
  onPaginationChange,
  onSearch,
  onSort,
  selectedRowKeys,
  onSelectChange,
  onRestore,
  onBatchRestore,
}) => {
  const batchDisabled = selectedRowKeys.length === 0;
  const columns: ProColumns<RecycleBinItem>[] = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 60,
      search: false,
    },
    {
      title: '资源类型',
      dataIndex: 'resourceTypeDisplay',
      width: 120,
      search: false,
      render: (_, record) => (
        <Tag color="blue">{record.resourceTypeDisplay}</Tag>
      ),
    },
    {
      title: '资源名称',
      dataIndex: 'originalName',
      width: 200,
      search: false,
      sorter: true,
    },
    {
      title: '删除人',
      dataIndex: 'deletedBy',
      width: 120,
      search: false,
    },
    {
      title: '删除时间',
      dataIndex: 'deletedAt',
      width: 160,
      search: false,
      valueType: 'dateTime',
      sorter: true,
    },
    {
      title: '剩余保留天数',
      dataIndex: 'daysUntilPurge',
      width: 120,
      search: false,
      render: (_, record) => {
        const days = record.daysUntilPurge;
        return (
          <span style={{ color: days < 7 ? '#ff4d4f' : undefined }}>
            {days} 天
          </span>
        );
      },
    },
    {
      title: '操作',
      width: 160,
      search: false,
      render: (_, record) => (
        <Space>
          <Popconfirm
            title="恢复该条目？"
            description="恢复后将以「停用」状态回到原列表，需手动启用。"
            onConfirm={() => onRestore(record.id)}
            okText="恢复"
            cancelText="取消"
          >
            <Button size="small" type="link">
              恢复
            </Button>
          </Popconfirm>
          <Tooltip title="等待资源对接">
            <Button size="small" disabled>
              永久删除
            </Button>
          </Tooltip>
        </Space>
      ),
    },
  ];

  return (
    <ProTable<RecycleBinItem>
      columns={columns}
      dataSource={dataSource}
      loading={loading}
      rowKey="id"
      search={false}
      options={false}
      onChange={(_pagination, _filters, sorter) => {
        const s = sorter as Record<string, any>;
        if (s.field) {
          onSort(s.field as string, s.order as 'ascend' | 'descend' | null);
        }
      }}
      toolbar={{
        search: (
          <Input.Search
            placeholder="搜索资源名称"
            allowClear
            onSearch={onSearch}
            style={{ width: 240 }}
          />
        ),
      }}
      rowSelection={{
        selectedRowKeys,
        onChange: (keys) => onSelectChange(keys as number[]),
        getCheckboxProps: () => ({ disabled: false }),
      }}
      tableAlertRender={({ selectedRowKeys: selectedKeys, onCleanSelected }) => (
        <Space size={24}>
          <span>
            已选择 {selectedKeys.length} 项
            <a style={{ marginInlineStart: 8 }} onClick={onCleanSelected}>
              取消选择
            </a>
          </span>
        </Space>
      )}
      tableAlertOptionRender={() => (
        <Space>
          <Popconfirm
            title={`批量恢复选中的 ${selectedRowKeys.length} 条？`}
            description="恢复后将以「停用」状态回到原列表，需手动启用。"
            onConfirm={onBatchRestore}
            okText="恢复"
            cancelText="取消"
          >
            <Button size="small" disabled={batchDisabled}>
              批量恢复
            </Button>
          </Popconfirm>
          <Tooltip title="等待资源对接">
            <Button size="small" disabled>
              批量永久删除
            </Button>
          </Tooltip>
        </Space>
      )}
      pagination={{
        current: pagination.current,
        pageSize: pagination.pageSize,
        total,
        showSizeChanger: true,
        showQuickJumper: true,
        onChange: onPaginationChange,
      }}
    />
  );
};

export default RecycleBinTable;

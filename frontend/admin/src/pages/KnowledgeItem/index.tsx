import {
  ActionType,
  ProColumns,
  ProTable,
} from '@ant-design/pro-components';
import { Button, Image, Input, message, Popconfirm, Space, Tag, Tooltip, TreeSelect } from 'antd';
import { useEffect, useRef, useState } from 'react';
import {
  batchActivate,
  batchDeactivate,
  batchSort,
  getKnowledgeItemById,
  listKnowledgeItems,
  type KnowledgeItemResponse,
  type BatchSortItem,
} from '@/services/knowledge-item';
import {
  getTree,
  convertToTreeDataActiveOnly,
  type CategoryTreeNode,
} from '@/services/knowledge-category';
import KnowledgeItemFormDrawer from './components/KnowledgeItemFormDrawer';
import { PlusOutlined, EditOutlined } from '@ant-design/icons';

const flattenTree = (nodes: CategoryTreeNode[]): Map<number, string> => {
  const map = new Map<number, string>();
  const walk = (list: CategoryTreeNode[]) => {
    list.forEach((n) => {
      map.set(n.id, n.name);
      if (n.children) walk(n.children);
    });
  };
  walk(nodes);
  return map;
};

const KnowledgeItemPage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerMode, setDrawerMode] = useState<'create' | 'edit'>('create');
  const [editData, setEditData] = useState<Record<string, any>>({});
  const [categoryTree, setCategoryTree] = useState<CategoryTreeNode[]>([]);
  const [categoryNameMap, setCategoryNameMap] = useState<Map<number, string>>(new Map());
  const [dataSource, setDataSource] = useState<KnowledgeItemResponse[]>([]);
  const [selectedRowKeys, setSelectedRowKeys] = useState<number[]>([]);

  useEffect(() => {
    getTree()
      .then((data) => {
        const treeData = data || [];
        setCategoryTree(treeData);
        setCategoryNameMap(flattenTree(treeData));
      })
      .catch(() => message.error('加载分类树失败'));
  }, []);

  const handleMove = async (record: KnowledgeItemResponse, direction: 'up' | 'down') => {
    const list = dataSource;
    const idx = list.findIndex((item) => item.id === record.id);
    if (idx === -1) return;
    const neighborIdx = direction === 'up' ? idx - 1 : idx + 1;
    if (neighborIdx < 0 || neighborIdx >= list.length) return;
    const neighbor = list[neighborIdx];
    try {
      await batchSort([
        { id: record.id, sortOrder: neighbor.sortOrder },
        { id: neighbor.id, sortOrder: record.sortOrder },
      ]);
      message.success('排序成功');
      actionRef.current?.reload();
    } catch (e: any) {
      message.error(e?.message || '排序失败');
    }
  };

  const handleBatchAction = async (action: 'activate' | 'deactivate') => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先选择条目');
      return;
    }
    try {
      if (action === 'activate') {
        await batchActivate(selectedRowKeys);
      } else {
        await batchDeactivate(selectedRowKeys);
      }
      message.success('操作成功');
      setSelectedRowKeys([]);
      actionRef.current?.reload();
    } catch (e: any) {
      message.error(e?.message || '操作失败');
    }
  };

  const columns: ProColumns<KnowledgeItemResponse>[] = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 60,
      search: false,
    },
    {
      title: '封面图',
      dataIndex: 'coverImageUrl',
      width: 60,
      search: false,
      render: (_, record) =>
        record.coverImageUrl ? (
          <Image src={record.coverImageUrl} width={50} height={50} style={{ objectFit: 'cover', borderRadius: 4 }} />
        ) : (
          <div style={{ width: 50, height: 50, background: '#f5f5f5', borderRadius: 4, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#bbb' }}>-</div>
        ),
    },
    {
      title: '标题',
      dataIndex: 'title',
      ellipsis: true,
      width: 160,
      render: (_, record) => (
        <Tooltip title={record.title}>
          <span>{record.title}</span>
        </Tooltip>
      ),
    },
    {
      title: '分类',
      dataIndex: 'categoryIds',
      width: 160,
      search: false,
      render: (_, record) => {
        const ids = record.categoryIds || [];
        const showIds = ids.slice(0, 2);
        const rest = ids.length - 2;
        return (
          <Space size={4} wrap>
            {showIds.map((cid) => (
              <Tag key={cid} color="blue">{categoryNameMap.get(cid) || `#${cid}`}</Tag>
            ))}
            {rest > 0 && <Tag>+{rest}</Tag>}
          </Space>
        );
      },
    },
    {
      title: '标签',
      dataIndex: 'tags',
      width: 120,
      search: false,
      render: (_, record) => (
        <Space size={4} wrap>
          {(record.tags || []).map((t) => (
            <Tag key={t}>{t}</Tag>
          ))}
        </Space>
      ),
    },
    {
      title: '排序号',
      dataIndex: 'sortOrder',
      width: 80,
      search: false,
      sorter: true,
    },
    {
      title: '分类',
      dataIndex: 'categoryId',
      width: 160,
      hideInTable: true,
      renderFormItem: () => (
        <TreeSelect
          treeData={convertToTreeDataActiveOnly(categoryTree)}
          placeholder="请选择分类"
          allowClear
          style={{ width: '100%' }}
        />
      ),
    },
    {
      title: '标签',
      dataIndex: 'tag',
      width: 120,
      hideInTable: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      valueEnum: {
        ACTIVE: { text: '启用', status: 'Success' },
        INACTIVE: { text: '停用', status: 'Default' },
      },
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 120,
      search: false,
      valueType: 'dateTime',
      sorter: true,
    },
    {
      title: '操作',
      width: 200,
      fixed: 'right',
      search: false,
      render: (_, record, index) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={async () => {
              try {
                const res = await getKnowledgeItemById(record.id);
                setEditData(res || {});
                setDrawerMode('edit');
                setDrawerOpen(true);
              } catch (e: any) {
                message.error(e?.message || '加载失败');
              }
            }}
          >
            编辑
          </Button>
          <Popconfirm
            title={record.status === 'ACTIVE' ? '确定停用？' : '确定启用？'}
            onConfirm={async () => {
              try {
                if (record.status === 'ACTIVE') {
                  await batchDeactivate([record.id]);
                } else {
                  await batchActivate([record.id]);
                }
                message.success('操作成功');
                actionRef.current?.reload();
              } catch (e: any) {
                message.error(e?.message || '操作失败');
              }
            }}
          >
            <Button type="link" size="small">
              {record.status === 'ACTIVE' ? '停用' : '启用'}
            </Button>
          </Popconfirm>
          <Button
            type="link"
            size="small"
            disabled={index === 0}
            onClick={() => handleMove(record, 'up')}
          >
            上移
          </Button>
          <Button
            type="link"
            size="small"
            disabled={index === dataSource.length - 1}
            onClick={() => handleMove(record, 'down')}
          >
            下移
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <>
      <ProTable<KnowledgeItemResponse>
        columns={columns}
        actionRef={actionRef}
        cardBordered
        request={async (params, sort) => {
          let sortField: string | undefined;
          let sortOrder: string | undefined;
          if (sort && typeof sort === 'object' && Object.keys(sort).length > 0) {
            const key = Object.keys(sort)[0];
            sortField = key;
            sortOrder = (sort as Record<string, string>)[key] === 'ascend' ? 'asc' : 'desc';
          }
          const res = await listKnowledgeItems({
            keyword: params.keyword,
            categoryId: params.categoryId,
            tag: params.tag,
            status: params.status,
            sort: sortField as KnowledgeItemResponse['sortOrder'] extends number ? 'sortOrder' | 'createdAt' | 'updatedAt' : any,
            order: sortOrder as 'asc' | 'desc' | undefined,
            page: (params.current || 1) - 1,
            size: params.pageSize || 20,
          });
          setDataSource(res?.content || []);
          return {
            data: res?.content || [],
            success: true,
            total: res?.totalElements || 0,
          };
        }}
        rowKey="id"
        search={{
          labelWidth: 'auto',
        }}
        pagination={{
          pageSize: 20,
        }}
        toolbar={{
          actions: [
            <Button
              key="create"
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setEditData({});
                setDrawerMode('create');
                setDrawerOpen(true);
              }}
            >
              新建
            </Button>,
            <Popconfirm
              key="batchActivate"
              title="确定批量启用？"
              onConfirm={() => handleBatchAction('activate')}
            >
              <Button key="btnActivate">批量启用</Button>
            </Popconfirm>,
            <Popconfirm
              key="batchDeactivate"
              title="确定批量停用？"
              onConfirm={() => handleBatchAction('deactivate')}
            >
              <Button key="btnDeactivate">批量停用</Button>
            </Popconfirm>,
          ],
        }}
        rowSelection={{
          selectedRowKeys,
          onChange: (keys) => setSelectedRowKeys(keys as number[]),
        }}
        columnsState={{
          persistenceKey: 'knowledge-item-table',
          persistenceType: 'localStorage',
        }}
      />
      <KnowledgeItemFormDrawer
        open={drawerOpen}
        mode={drawerMode}
        initialValues={editData}
        onClose={() => setDrawerOpen(false)}
        onSubmit={() => {
          setDrawerOpen(false);
          actionRef.current?.reload();
        }}
      />
    </>
  );
};

export default KnowledgeItemPage;

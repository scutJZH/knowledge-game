import {
  ActionType,
  ProColumns,
  ProTable,
} from '@ant-design/pro-components';
import { Button, Dropdown, Image, Input, message, Modal, Popconfirm, Space, Tag, Tooltip, TreeSelect } from 'antd';
import { useEffect, useRef, useState } from 'react';
import {
  batchActivate,
  batchDeactivate,
  batchSort,
  downloadImportTemplate,
  getKnowledgeItemById,
  importExcel,
  downloadImportMarkdownZipTemplate,
  importMarkdownZip,
  listKnowledgeItems,
  type KnowledgeItemListResponse,
  type BatchSortItem,
  type KnowledgeItemQuery,
  type KnowledgeItemImportResult,
} from '@/services/knowledge-item';
import {
  getTree,
  convertToTreeDataActiveOnly,
  buildCategoryPathMap,
  type CategoryTreeNode,
} from '@/services/knowledge-category';
import KnowledgeItemFormDrawer from './components/KnowledgeItemFormDrawer';
import ImportResultModal from './components/ImportResultModal';
import { PlusOutlined, EditOutlined, EyeOutlined, DownloadOutlined, UploadOutlined } from '@ant-design/icons';

const KnowledgeItemPage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerMode, setDrawerMode] = useState<'create' | 'edit'>('create');
  const [editData, setEditData] = useState<Record<string, any>>({});
  const [categoryTree, setCategoryTree] = useState<CategoryTreeNode[]>([]);
  const idToPathMapRef = useRef<Map<number, string>>(new Map());
  const [dataSource, setDataSource] = useState<KnowledgeItemListResponse[]>([]);
  const [selectedRowKeys, setSelectedRowKeys] = useState<number[]>([]);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewTitle, setPreviewTitle] = useState('');
  const [previewHtml, setPreviewHtml] = useState('');
  const [importResult, setImportResult] = useState<KnowledgeItemImportResult | null>(null);
  const [importModalOpen, setImportModalOpen] = useState(false);

  useEffect(() => {
    getTree()
      .then((data) => {
        const treeData = data || [];
        setCategoryTree(treeData);
        idToPathMapRef.current = buildCategoryPathMap(treeData);
      })
      .catch(() => {}); // 分类树加载失败，静默处理（错误已由全局拦截器展示）
  }, []);

  const handleMove = async (record: KnowledgeItemListResponse, direction: 'up' | 'down') => {
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
    } catch {
      // 错误已由全局拦截器展示
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
    } catch {
      // 错误已由全局拦截器展示
    }
  };

  const handleDownloadTemplate = async () => {
    try {
      const blob = await downloadImportTemplate();
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = 'knowledge_item_import_template.xlsx';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch (e: any) {
      message.error(e?.message || '下载模板失败');
    }
  };

  const columns: ProColumns<KnowledgeItemListResponse>[] = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 60,
      search: false,
      sorter: true,
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
      sorter: true,
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
      sorter: true,
      render: (_, record) => {
        const ids = record.categoryIds || [];
        if (ids.length === 0) return <span style={{ color: '#ccc' }}>-</span>;
        const map = idToPathMapRef.current;
        const shown = ids.slice(0, 2);
        const rest = ids.length - 2;
        return (
          <Space size={4} wrap>
            {shown.map((id) => {
              const path = map.get(id) || `#${id}`;
              return (
                <Tooltip key={id} title={path}>
                  <Tag>{path}</Tag>
                </Tooltip>
              );
            })}
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
      sorter: true,
      valueEnum: {
        ACTIVE: { text: '启用', status: 'Success' },
        INACTIVE: { text: '停用', status: 'Default' },
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 120,
      search: false,
      valueType: 'dateTime',
      sorter: true,
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
            icon={<EyeOutlined />}
            onClick={async () => {
              try {
                const res = await getKnowledgeItemById(record.id);
                setPreviewTitle(res?.title || '');
                setPreviewHtml(res?.contentHtml || '');
                setPreviewOpen(true);
              } catch {
                // 错误已由全局拦截器展示
              }
            }}
          />
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
              } catch {
                // 错误已由全局拦截器展示
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
              } catch {
                // 错误已由全局拦截器展示
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
      <ProTable<KnowledgeItemListResponse>
        columns={columns}
        actionRef={actionRef}
        cardBordered
        request={async (params, sort) => {
          let sortField: string | undefined;
          let sortOrder: string | undefined;
          if (sort && typeof sort === 'object' && Object.keys(sort).length > 0) {
            const key = Object.keys(sort)[0];
            sortField = key === 'categoryIds' ? 'categoryName' : key;
            sortOrder = (sort as Record<string, string>)[key] === 'ascend' ? 'asc' : 'desc';
          }
          const res = await listKnowledgeItems({
            keyword: params.keyword,
            categoryId: params.categoryId,
            tag: params.tag,
            status: params.status,
            sort: sortField as KnowledgeItemQuery['sort'],
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
            <Dropdown
              key="downloadTemplate"
              menu={{
                items: [
                  {
                    key: 'excel',
                    label: 'Excel 模板',
                    icon: <DownloadOutlined />,
                    onClick: handleDownloadTemplate,
                  },
                  {
                    key: 'markdown',
                    label: 'Markdown zip 模板',
                    icon: <DownloadOutlined />,
                    onClick: async () => {
                      try {
                        const blob = await downloadImportMarkdownZipTemplate();
                        const url = window.URL.createObjectURL(blob);
                        const link = document.createElement('a');
                        link.href = url;
                        link.download = 'knowledge_item_import_markdown_template.zip';
                        document.body.appendChild(link);
                        link.click();
                        document.body.removeChild(link);
                        window.URL.revokeObjectURL(url);
                      } catch (e: any) {
                        message.error(e?.message || '下载模板失败');
                      }
                    },
                  },
                ],
              }}
            >
              <Button icon={<DownloadOutlined />}>下载模板</Button>
            </Dropdown>,
            <Dropdown
              key="batchImport"
              menu={{
                items: [
                  {
                    key: 'excel',
                    label: 'Excel 批量导入',
                    icon: <UploadOutlined />,
                    onClick: () => {
                      const input = document.createElement('input');
                      input.type = 'file';
                      input.accept = '.xlsx';
                      input.onchange = async () => {
                        const file = input.files?.[0];
                        if (!file) return;
                        if (file.size > 10 * 1024 * 1024) {
                          message.error('文件大小不能超过 10MB');
                          return;
                        }
                        try {
                          const result = await importExcel(file);
                          setImportResult(result);
                          setImportModalOpen(true);
                          actionRef.current?.reload();
                        } catch (e: any) {
                          message.error(e?.message || 'Excel 导入失败');
                        }
                      };
                      input.click();
                    },
                  },
                  {
                    key: 'markdown',
                    label: 'Markdown zip 导入',
                    icon: <UploadOutlined />,
                    onClick: () => {
                      const input = document.createElement('input');
                      input.type = 'file';
                      input.accept = '.zip';
                      input.onchange = async () => {
                        const file = input.files?.[0];
                        if (!file) return;
                        if (file.size > 20 * 1024 * 1024) {
                          message.error('文件大小不能超过 20MB');
                          return;
                        }
                        try {
                          const result = await importMarkdownZip(file);
                          setImportResult(result);
                          setImportModalOpen(true);
                          actionRef.current?.reload();
                        } catch (e: any) {
                          message.error(e?.message || 'Markdown zip 导入失败');
                        }
                      };
                      input.click();
                    },
                  },
                ],
              }}
            >
              <Button icon={<UploadOutlined />}>批量导入</Button>
            </Dropdown>,
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
      <Modal
        title={previewTitle || '预览'}
        open={previewOpen}
        onCancel={() => setPreviewOpen(false)}
        footer={null}
        width={900}
      >
        {previewHtml ? (
          <div
            dangerouslySetInnerHTML={{ __html: previewHtml }}
            style={{ maxHeight: '70vh', overflow: 'auto', padding: 16 }}
          />
        ) : (
          <div style={{ textAlign: 'center', color: '#999', padding: 40 }}>暂无内容</div>
        )}
      </Modal>
      <ImportResultModal
        open={importModalOpen}
        result={importResult}
        onClose={() => setImportModalOpen(false)}
      />
    </>
  );
};

export default KnowledgeItemPage;

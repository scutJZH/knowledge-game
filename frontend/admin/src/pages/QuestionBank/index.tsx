import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Button, message, Popconfirm, Select, Space, Tag, Tooltip, TreeSelect, Upload } from 'antd';
import { ReloadOutlined, UploadOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';

import type { QuestionResponse, QuestionQuery, QuestionImportResult } from '@/services/questionBank';
import {
  DIFFICULTY_OPTIONS,
  QUESTION_TYPE_OPTIONS,
  QUESTION_STATUS_OPTIONS,
  batchActivate,
  batchDeactivate,
  downloadImportTemplate,
  getQuestionById,
  importQuestions,
  listQuestions,
} from '@/services/questionBank';
import { getTree } from '@/services/knowledge-category';
import type { CategoryTreeNode } from '@/services/knowledge-category';
import QuestionFormDrawer from './components/QuestionFormDrawer';
import ImportResultModal from './components/ImportResultModal';

/** 将分类树扁平化为 id → name 的 Map */
function flattenTree(nodes: CategoryTreeNode[]): Map<number, string> {
  const map = new Map<number, string>();
  const walk = (list: CategoryTreeNode[]) => {
    for (const node of list) {
      map.set(node.id, node.name);
      if (node.children && node.children.length > 0) {
        walk(node.children);
      }
    }
  };
  walk(nodes);
  return map;
}

/** 排序字段选项 */
const SORT_OPTIONS = [
  { label: '更新时间', value: 'updatedAt' },
  { label: '创建时间', value: 'createdAt' },
  { label: '难度', value: 'difficulty' },
];

/** 题库管理页 */
const QuestionBank: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerMode, setDrawerMode] = useState<'create' | 'edit'>('create');
  const [editingQuestion, setEditingQuestion] = useState<QuestionResponse | undefined>();
  const [categoryTree, setCategoryTree] = useState<CategoryTreeNode[]>([]);
  const idToNameMapRef = useRef<Map<number, string>>(new Map());
  const treeLoadedRef = useRef(false);

  /** 导入相关状态 */
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const [importModalOpen, setImportModalOpen] = useState(false);
  const [importResult, setImportResult] = useState<QuestionImportResult | null>(null);

  /** 批量选中 */
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);

  /** 排序/筛选状态 */
  const [sortField, setSortField] = useState<string>('updatedAt');
  const [sortOrder, setSortOrder] = useState<string>('desc');

  /** 页面挂载时一次性加载分类树 */
  useEffect(() => {
    if (treeLoadedRef.current) return;
    treeLoadedRef.current = true;
    getTree()
      .then((tree) => {
        setCategoryTree(tree);
        idToNameMapRef.current = flattenTree(tree);
      })
      .catch(() => {
        // 错误已由 request 拦截器展示，分类相关 UI 降级显示 id
      });
  }, []);

  /** 列定义（依赖 categoryTree 以支持搜索表单中的 TreeSelect） */
  const columns: ProColumns<QuestionResponse>[] = useMemo(
    () => [
      { title: 'ID', dataIndex: 'id', search: false, width: 80 },
      {
        title: '题型',
        dataIndex: 'type',
        width: 80,
        valueType: 'select',
        valueEnum: Object.fromEntries(
          QUESTION_TYPE_OPTIONS.map((o) => [o.value, { text: o.label }]),
        ),
        render: (_, record) => {
          const opt = QUESTION_TYPE_OPTIONS.find((o) => o.value === record.type);
          return <Tag color={opt?.color}>{opt?.label || record.type}</Tag>;
        },
      },
      {
        title: '题目内容',
        dataIndex: 'keyword',
        search: true,
        render: (_, record) => {
          const text = record.content;
          if (text.length <= 50) return <span>{text}</span>;
          return (
            <Tooltip title={text}>
              <span>{text.slice(0, 50)}...</span>
            </Tooltip>
          );
        },
      },
      {
        title: '难度',
        dataIndex: 'difficulty',
        width: 80,
        valueType: 'select',
        valueEnum: Object.fromEntries(
          DIFFICULTY_OPTIONS.map((o) => [String(o.value), { text: o.label }]),
        ),
        render: (_, record) => {
          const opt = DIFFICULTY_OPTIONS.find((o) => o.value === record.difficulty);
          return <Tag color={opt?.color}>{opt?.label || record.difficulty}</Tag>;
        },
      },
      {
        title: '分类',
        dataIndex: 'categoryId',
        hideInTable: true,
        renderFormItem: () => (
          <TreeSelect
            placeholder="按分类筛选"
            treeData={categoryTree.map((node) => ({
              title: node.name,
              value: node.id,
              key: node.id,
              children: node.children
                ? node.children.map((child) => ({
                    title: child.name,
                    value: child.id,
                    key: child.id,
                    children: child.children
                      ? child.children.map((gchild) => ({
                          title: gchild.name,
                          value: gchild.id,
                          key: gchild.id,
                        }))
                      : undefined,
                  }))
                : undefined,
            }))}
            allowClear
            style={{ width: '100%' }}
          />
        ),
      },
      {
        title: '分类',
        dataIndex: 'categoryIds',
        search: false,
        render: (_, record) => {
          const ids = record.categoryIds || [];
          if (ids.length === 0) return <span style={{ color: '#ccc' }}>-</span>;
          const map = idToNameMapRef.current;
          const shown = ids.slice(0, 2);
          const rest = ids.length - 2;
          return (
            <Space size={4} wrap>
              {shown.map((id) => (
                <Tag key={id}>{map.get(id) || `#${id}`}</Tag>
              ))}
              {rest > 0 && <Tag>+{rest}</Tag>}
            </Space>
          );
        },
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 80,
        initialValue: undefined,
        valueType: 'select',
        valueEnum: Object.fromEntries(
          QUESTION_STATUS_OPTIONS.map((o) => [o.value, { text: o.label }]),
        ),
        render: (_, record) => {
          const opt = QUESTION_STATUS_OPTIONS.find((o) => o.value === record.status);
          return <Tag color={opt?.color}>{opt?.label || record.status}</Tag>;
        },
      },
      {
        title: '更新时间',
        dataIndex: 'updatedAt',
        search: false,
        width: 120,
        valueType: 'dateTime',
      },
      {
        title: '操作',
        key: 'action',
        search: false,
        fixed: 'right',
        width: 140,
        render: (_, record) => (
          <Space>
            <a onClick={() => handleEdit(record.id)}>编辑</a>
            {record.status === 'ACTIVE' ? (
              <Popconfirm
                title="确定停用该题目吗？"
                onConfirm={() => handleToggleStatus(record.id, 'INACTIVE')}
              >
                <a>停用</a>
              </Popconfirm>
            ) : (
              <Popconfirm
                title="确定启用该题目吗？"
                onConfirm={() => handleToggleStatus(record.id, 'ACTIVE')}
              >
                <a style={{ color: '#52c41a' }}>启用</a>
              </Popconfirm>
            )}
          </Space>
        ),
      },
    ],
    [categoryTree],
  );

  /** 打开编辑抽屉：拉取详情 → 打开 */
  const handleEdit = async (id: number) => {
    try {
      const detail = await getQuestionById(id);
      setEditingQuestion(detail);
      setDrawerMode('edit');
      setDrawerOpen(true);
    } catch {
      // 错误已由 request 拦截器展示
    }
  };

  /** 新建题目 */
  const handleCreate = () => {
    setEditingQuestion(undefined);
    setDrawerMode('create');
    setDrawerOpen(true);
  };

  /** 抽屉提交成功后刷新列表并关闭 */
  const handleDrawerSubmit = () => {
    setDrawerOpen(false);
    setEditingQuestion(undefined);
    actionRef.current?.reload();
  };

  /** 抽屉关闭 */
  const handleDrawerClose = () => {
    setDrawerOpen(false);
    setEditingQuestion(undefined);
  };

  /** 单条启用/停用 */
  const handleToggleStatus = async (id: number, targetStatus: string) => {
    try {
      if (targetStatus === 'ACTIVE') {
        await batchActivate([id]);
      } else {
        await batchDeactivate([id]);
      }
      message.success(targetStatus === 'ACTIVE' ? '已启用' : '已停用');
      actionRef.current?.reload();
    } catch {
      // 错误已由 request 拦截器展示
    }
  };

  /** 批量启用 */
  const handleBatchActivate = async () => {
    try {
      await batchActivate(selectedRowKeys.map(Number));
      message.success(`已启用 ${selectedRowKeys.length} 道题目`);
      setSelectedRowKeys([]);
      actionRef.current?.reload();
    } catch {
      // 错误已由 request 拦截器展示
    }
  };

  /** 批量停用 */
  const handleBatchDeactivate = async () => {
    try {
      await batchDeactivate(selectedRowKeys.map(Number));
      message.success(`已停用 ${selectedRowKeys.length} 道题目`);
      setSelectedRowKeys([]);
      actionRef.current?.reload();
    } catch {
      // 错误已由 request 拦截器展示
    }
  };

  /** 下载导入模板 */
  const handleDownloadTemplate = async () => {
    try {
      const blob = await downloadImportTemplate();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = '题库导入模板.xlsx';
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      // 错误已由 request 拦截器展示
    }
  };

  /** 导入前校验：仅 .xlsx，≤ 10MB */
  const handleBeforeUpload = (file: File): boolean => {
    if (!file.name.endsWith('.xlsx')) {
      message.error('仅支持 .xlsx 格式');
      return false;
    }
    if (file.size > 10 * 1024 * 1024) {
      message.error('文件大小不能超过 10MB');
      return false;
    }
    setPendingFile(file);
    return false; // 阻止自动上传，由 Popconfirm 确认后手动提交
  };

  /** 确认导入 */
  const handleImportConfirm = async () => {
    if (!pendingFile) return;
    try {
      const result = await importQuestions(pendingFile);
      setImportResult(result);
      setImportModalOpen(true);
      actionRef.current?.reload();
    } catch {
      // 错误已由 request 拦截器展示
    }
    setPendingFile(null);
  };

  return (
    <>
      <ProTable<QuestionResponse, QuestionQuery>
        headerTitle="题库管理"
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        rowSelection={{
          type: 'checkbox',
          selectedRowKeys,
          preserveSelectedRowKeys: true,
          onChange: (keys) => setSelectedRowKeys(keys),
        }}
        tableAlertRender={({ selectedRowKeys: keys }) => (
          <Space>
            <span>已选 {keys.length} 项</span>
          </Space>
        )}
        tableAlertOptionRender={({ selectedRowKeys: keys }) => (
          <Space size={8}>
            <Popconfirm
              title={`确定对选中的 ${keys.length} 道题目执行启用？`}
              onConfirm={handleBatchActivate}
            >
              <Button size="small" type="link">
                批量启用
              </Button>
            </Popconfirm>
            <Popconfirm
              title={`确定对选中的 ${keys.length} 道题目执行停用？`}
              onConfirm={handleBatchDeactivate}
            >
              <Button size="small" type="link">
                批量停用
              </Button>
            </Popconfirm>
          </Space>
        )}
        request={async (params) => {
          const { current, pageSize, keyword, type, difficulty, status, categoryId } =
            params;
          const result = await listQuestions({
            page: (current ?? 1) - 1,
            size: pageSize,
            keyword,
            type: type as QuestionQuery['type'],
            difficulty: difficulty
              ? (Number(difficulty) as 1 | 2 | 3)
              : undefined,
            categoryId: categoryId ? Number(categoryId) : undefined,
            status: status as QuestionQuery['status'],
            sort: sortField as QuestionQuery['sort'],
            order: sortOrder as QuestionQuery['order'],
          });
          return {
            data: result.content,
            total: result.totalElements,
            success: true,
          };
        }}
        toolBarRender={() => [
          <Button
            key="create"
            type="primary"
            className="btn-create-question"
            onClick={handleCreate}
          >
            新建题目
          </Button>,
          <Select
            key="sort"
            value={sortField}
            onChange={(val) => {
              setSortField(val);
              setTimeout(() => actionRef.current?.reload(), 0);
            }}
            options={SORT_OPTIONS}
            style={{ width: 120 }}
          />,
          <Button
            key="order"
            onClick={() => {
              setSortOrder((prev) => (prev === 'asc' ? 'desc' : 'asc'));
              setTimeout(() => actionRef.current?.reload(), 0);
            }}
            icon={<ReloadOutlined rotate={sortOrder === 'desc' ? 0 : 180} />}
          >
            {sortOrder === 'asc' ? '升序' : '降序'}
          </Button>,
          <Button key="download-template" onClick={handleDownloadTemplate}>
            下载模板
          </Button>,
          <Popconfirm
            key="import"
            title="确认导入？"
            description="将导入 Excel 文件中的全部题目"
            onConfirm={handleImportConfirm}
            disabled={!pendingFile}
          >
            <Upload
              accept=".xlsx"
              maxCount={1}
              beforeUpload={handleBeforeUpload}
              fileList={[]}
              showUploadList={false}
            >
              <Button icon={<UploadOutlined />}>批量导入</Button>
            </Upload>
          </Popconfirm>,
        ]}
      />

      <ImportResultModal
        open={importModalOpen}
        result={importResult}
        onClose={() => {
          setImportModalOpen(false);
          setImportResult(null);
        }}
      />

      <QuestionFormDrawer
        open={drawerOpen}
        mode={drawerMode}
        initialValues={editingQuestion}
        categoryTree={categoryTree}
        onSubmit={handleDrawerSubmit}
        onClose={handleDrawerClose}
      />
    </>
  );
};

export default QuestionBank;

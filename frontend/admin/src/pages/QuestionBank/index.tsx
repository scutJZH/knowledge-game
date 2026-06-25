import React, { useEffect, useRef, useState } from 'react';
import { Button, message, Modal, Popconfirm, Space, Tag, Tooltip, TreeSelect, Upload } from 'antd';
import { DeleteOutlined, UploadOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';

import type { QuestionResponse, QuestionQuery, QuestionImportResult } from '@/services/questionBank';
import {
  DIFFICULTY_OPTIONS,
  QUESTION_TYPE_OPTIONS,
  QUESTION_STATUS_OPTIONS,
  batchActivate,
  batchDeactivate,
  deleteQuestion,
  downloadImportTemplate,
  getQuestionById,
  importQuestions,
  listQuestions,
} from '@/services/questionBank';
import { getTree, convertToTreeData, buildCategoryPathMap } from '@/services/knowledge-category';
import type { CategoryTreeNode } from '@/services/knowledge-category';
import QuestionFormDrawer from './components/QuestionFormDrawer';
import ImportResultModal from './components/ImportResultModal';

/** 题库管理页 */
const QuestionBank: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerMode, setDrawerMode] = useState<'create' | 'edit'>('create');
  const [editingQuestion, setEditingQuestion] = useState<QuestionResponse | undefined>();
  const [categoryTree, setCategoryTree] = useState<CategoryTreeNode[]>([]);
  const idToPathMapRef = useRef<Map<number, string>>(new Map());
  const treeLoadedRef = useRef(false);

  /** 导入相关状态 */
  const [importModalOpen, setImportModalOpen] = useState(false);
  const [importResult, setImportResult] = useState<QuestionImportResult | null>(null);

  /** 批量选中 */
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);

  /** 页面挂载时一次性加载分类树 */
  useEffect(() => {
    if (treeLoadedRef.current) return;
    treeLoadedRef.current = true;
    getTree()
      .then((tree) => {
        setCategoryTree(tree);
        idToPathMapRef.current = buildCategoryPathMap(tree);
      })
      .catch(() => {
        // 错误已由 request 拦截器展示，分类相关 UI 降级显示 id
      });
  }, []);

  /** 列定义 */
  const columns: ProColumns<QuestionResponse>[] = [
      { title: 'ID', dataIndex: 'id', search: false, width: 80, sorter: true },
      {
        title: '题型',
        dataIndex: 'type',
        width: 80,
        valueType: 'select',
        valueEnum: Object.fromEntries(
          QUESTION_TYPE_OPTIONS.map((o) => [o.value, { text: o.label }]),
        ),
        sorter: true,
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
        sorter: true,
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
            treeData={convertToTreeData(categoryTree)}
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
        title: '状态',
        dataIndex: 'status',
        width: 80,
        initialValue: 'ACTIVE',
        valueType: 'select',
        valueEnum: {
          ALL: { text: '全部' },
          ...Object.fromEntries(
            QUESTION_STATUS_OPTIONS.map((o) => [o.value, { text: o.label }]),
          ),
        },
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
        sorter: true,
        defaultSortOrder: 'descend',
        valueType: 'dateTime',
      },
      {
        title: '操作',
        key: 'action',
        search: false,
        fixed: 'right',
        width: 180,
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
            <Popconfirm
              title="确定删除该题目吗？删除后将移入回收站。"
              onConfirm={() => handleDelete(record.id)}
            >
              <a style={{ color: '#ff4d4f' }}><DeleteOutlined /> 删除</a>
            </Popconfirm>
          </Space>
        ),
      },
  ];

  /** 删除题目（移入回收站） */
  const handleDelete = async (id: number) => {
    try {
      await deleteQuestion(id);
      message.success('已移入回收站');
      actionRef.current?.reload();
    } catch {
      // 错误已由 request 拦截器展示
    }
  };

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
      // 延迟释放避免部分浏览器下载未启动就被回收
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch {
      // 错误已由 request 拦截器展示
    }
  };

  /** 执行导入（收到文件后弹确认 → 确认后调 API） */
  const executeImport = (file: File) => {
    Modal.confirm({
      title: '确认导入？',
      content: '将导入 Excel 文件中的全部题目，已存在的题目不会覆盖。',
      onOk: async () => {
        try {
          const result = await importQuestions(file);
          setImportResult(result);
          setImportModalOpen(true);
          actionRef.current?.reload();
        } catch {
          // 错误已由 request 拦截器展示
        }
      },
    });
  };

  /** 导入前校验：扩展名 + 大小 */
  const handleBeforeUpload = (file: File): boolean => {
    if (!file.name.endsWith('.xlsx')) {
      message.error('仅支持 .xlsx 格式');
      return false;
    }
    if (file.size > 10 * 1024 * 1024) {
      message.error('文件大小不能超过 10MB');
      return false;
    }
    // setTimeout 将 Modal.confirm 推到事件循环外，避免在 Upload 的合成事件回调中触发导致焦点异常
    setTimeout(() => executeImport(file), 0);
    return false; // 阻止自动上传
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
        request={async (params, sort) => {
          const { current, pageSize, keyword, type, difficulty, status, categoryId } =
            params;
          // 从 ProTable 列头排序中提取 sort/order
          let sortField: string | undefined;
          let sortOrder: string | undefined;
          if (sort && typeof sort === 'object' && Object.keys(sort).length > 0) {
            const key = Object.keys(sort)[0];
            sortField = key;
            sortOrder = (sort as Record<string, string>)[key] === 'ascend' ? 'asc' : 'desc';
          }
          const result = await listQuestions({
            page: (current ?? 1) - 1,
            size: pageSize,
            keyword,
            type: type as QuestionQuery['type'],
            difficulty: difficulty
              ? (Number(difficulty) as 1 | 2 | 3)
              : undefined,
            categoryId: categoryId ? Number(categoryId) : undefined,
            status: status && String(status) !== 'ALL' ? (status as QuestionQuery['status']) : undefined,
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
          <Button key="download-template" onClick={handleDownloadTemplate}>
            下载模板
          </Button>,
          <Upload
            key="import"
            accept=".xlsx"
            maxCount={1}
            beforeUpload={handleBeforeUpload}
            fileList={[]}
            showUploadList={false}
          >
            <Button icon={<UploadOutlined />}>批量导入</Button>
          </Upload>,
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

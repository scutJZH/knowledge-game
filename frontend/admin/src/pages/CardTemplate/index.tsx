import React, { useRef, useState } from 'react';
import { Button, message, Popconfirm, Space, Tag } from 'antd';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  ProForm,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';

import {
  createCardTemplate,
  getCardTemplateById,
  listCardTemplates,
  updateCardTemplate,
  addOrUpdateStarImage,
} from '@/services/cardTemplate';
import type {
  CardRarity,
  CardTemplateListResponse,
  CardTemplateResponse,
  CardTemplateStatus,
  CreateCardTemplateRequest,
  StarImageResponse,
  UpdateCardTemplateRequest,
} from '@/services/cardTemplate';
import { listIpSeries } from '@/services/ipSeries';
import StarImageUpload from './components/StarImageUpload';

/** 表单值类型（基础字段 + 5 个星级图片字段） */
type CardTemplateFormValues = UpdateCardTemplateRequest & {
  ipSeriesId?: number;
  starImage_1?: string;
  starImage_2?: string;
  starImage_3?: string;
  starImage_4?: string;
  starImage_5?: string;
};

/** 将星级图片列表转换为 starImage_N 映射 */
function buildStarImageMap(starImages?: StarImageResponse[]): Record<string, string | undefined> {
  const map: Record<string, string | undefined> = {};
  for (let level = 1; level <= 5; level++) {
    const starImage = starImages?.find((s) => s.starLevel === level);
    map[`starImage_${level}`] = starImage?.imageUrl;
  }
  return map;
}

/** 稀有度 Tag 颜色映射 */
const RARITY_COLOR_MAP: Record<CardRarity, string> = {
  N: 'default',
  R: 'blue',
  SR: 'purple',
  SSR: 'gold',
  SP: 'cyan',
};

/** SP 稀有度钻石白样式：蓝白渐变 + 微光 */
const SP_TAG_STYLE: React.CSSProperties = {
  background: 'linear-gradient(135deg, #ffffff 0%, #e8f4fc 25%, #d0e3f0 50%, #f8fbff 75%, #e6f0f8 100%)',
  border: '1px solid #b8d0e2',
  color: '#1a2b3c',
  fontWeight: 600,
  boxShadow: '0 0 4px rgba(180, 210, 240, 0.4), inset 0 0 3px rgba(255,255,255,0.8)',
};

/** 稀有度下拉选项 */
const RARITY_OPTIONS = ['N', 'R', 'SR', 'SSR', 'SP'];

/** 状态 Tag 颜色映射 */
const STATUS_COLOR_MAP: Record<CardTemplateStatus, string> = {
  ACTIVE: 'green',
  INACTIVE: 'default',
};

/** 卡牌管理页 */
const CardTemplate: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [modalOpen, setModalOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<CardTemplateResponse | null>(null);
  const [submitLoading, setSubmitLoading] = useState(false);
  /** 编辑模式下记录初始星级图片快照，用于变更比对 */
  const initialStarImagesRef = useRef<Record<string, string | undefined>>({});

  const columns: ProColumns<CardTemplateListResponse>[] = [
    { title: 'ID', dataIndex: 'id', search: false, width: 80 },
    { title: '编码', dataIndex: 'code', search: false },
    { title: '名称', dataIndex: 'name' },
    {
      title: 'IP系列',
      dataIndex: 'ipSeriesName',
      search: false,
    },
    {
      title: 'IP系列',
      dataIndex: 'ipSeriesId',
      hideInTable: true,
      valueType: 'select',
      request: async () => {
        const res = await listIpSeries({ status: 'ACTIVE', size: 1000 });
        return (res.content || []).map((item) => ({
          label: item.name,
          value: item.id,
        }));
      },
    },
    {
      title: '稀有度',
      dataIndex: 'rarity',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        RARITY_OPTIONS.map((r) => [r, { text: r }]),
      ),
      render: (_, record) =>
        record.rarity === 'SP' ? (
          <Tag style={SP_TAG_STYLE}>{record.rarity}</Tag>
        ) : (
          <Tag color={RARITY_COLOR_MAP[record.rarity]}>{record.rarity}</Tag>
        ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      valueType: 'select',
      initialValue: 'ACTIVE',
      valueEnum: {
        ALL: { text: '全部' },
        ACTIVE: { text: '启用', status: 'Success' },
        INACTIVE: { text: '停用', status: 'Default' },
      },
      render: (_, record) => (
        <Tag color={STATUS_COLOR_MAP[record.status]}>
          {record.status === 'ACTIVE' ? '启用' : '停用'}
        </Tag>
      ),
    },
    { title: '创建时间', dataIndex: 'createdAt', search: false, valueType: 'dateTime' },
    { title: '更新时间', dataIndex: 'updatedAt', search: false, valueType: 'dateTime' },
    {
      title: '操作',
      key: 'action',
      search: false,
      render: (_, record) => (
        <Space>
          <a
            onClick={() => {
              handleEdit(record.id);
            }}
          >
            编辑
          </a>
          {record.status === 'ACTIVE' ? (
            <Popconfirm
              title="确定停用该卡牌模板吗？"
              onConfirm={() => handleToggleStatus(record.id, 'INACTIVE')}
            >
              <a>停用</a>
            </Popconfirm>
          ) : (
            <Popconfirm
              title="确定启用该卡牌模板吗？"
              onConfirm={() => handleToggleStatus(record.id, 'ACTIVE')}
            >
              <a style={{ color: '#52c41a' }}>启用</a>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  /** 切换卡牌模板启用/停用状态 */
  const handleToggleStatus = async (id: number, status: CardTemplateStatus) => {
    try {
      await updateCardTemplate(id, { status });
      message.success(status === 'ACTIVE' ? '已启用' : '已停用');
      actionRef.current?.reload();
    } catch {
      // 错误已由 request 拦截器展示
    }
  };

  /** 打开编辑弹窗：拉取详情 → 预填表单 + 记录初始星级快照 */
  const handleEdit = async (id: number) => {
    try {
      const detail = await getCardTemplateById(id);
      setEditingRecord(detail);
      initialStarImagesRef.current = buildStarImageMap(detail.starImages);
      setModalOpen(true);
    } catch {
      // 错误已由 request 拦截器展示
    }
  };

  /** 创建/编辑提交 */
  const handleFinish = async (values: CardTemplateFormValues) => {
    setSubmitLoading(true);
    try {
      if (editingRecord) {
        // ---- 编辑模式 ----
        const {
          starImage_1, starImage_2, starImage_3, starImage_4, starImage_5,
          ipSeriesId, ...baseFields
        } = values;

        // 第一步：PUT 更新基础字段
        await updateCardTemplate(editingRecord.id, baseFields as UpdateCardTemplateRequest);

        // 第二步：比对星级图片变更，对变更项调用 addOrUpdateStarImage
        const errors: string[] = [];
        const currentStars: Record<string, string | undefined> = {
          starImage_1, starImage_2, starImage_3, starImage_4, starImage_5,
        };

        for (let level = 1; level <= 5; level++) {
          const key = `starImage_${level}`;
          const currentUrl = currentStars[key];
          const initialUrl = initialStarImagesRef.current[key];

          if (currentUrl && currentUrl !== initialUrl) {
            try {
              await addOrUpdateStarImage(editingRecord.id, {
                starLevel: level,
                imageUrl: currentUrl,
              });
            } catch (e: any) {
              errors.push(`★${level}: ${e.message || '更新失败'}`);
            }
          }
        }

        if (errors.length > 0) {
          message.error(`星级图片更新失败：${errors.join('；')}`);
          return false; // 保持弹窗打开，允许用户重试
        }

        message.success('更新成功');
      } else {
        // ---- 创建模式 ----
        const {
          starImage_1, starImage_2, starImage_3, starImage_4, starImage_5,
          ...baseFields
        } = values;

        // 收集已上传的星级图片
        const starImages: { starLevel: number; imageUrl: string }[] = [];
        const starMap: Record<string, string | undefined> = {
          starImage_1, starImage_2, starImage_3, starImage_4, starImage_5,
        };
        for (let level = 1; level <= 5; level++) {
          const url = starMap[`starImage_${level}`];
          if (url) {
            starImages.push({ starLevel: level, imageUrl: url });
          }
        }

        await createCardTemplate({
          ...baseFields,
          starImages,
        } as CreateCardTemplateRequest);
        message.success('创建成功');
      }

      setModalOpen(false);
      setEditingRecord(null);
      actionRef.current?.reload();
      return true;
    } catch {
      // 错误已由 request 拦截器展示，仅需返回 false 保持弹窗打开
      return false;
    } finally {
      setSubmitLoading(false);
    }
  };

  /** 构建编辑模式下的初始值 */
  const buildInitialValues = (): CardTemplateFormValues => {
    if (!editingRecord) return { status: 'ACTIVE' };
    return {
      ipSeriesId: editingRecord.ipSeriesId,
      code: editingRecord.code,
      name: editingRecord.name,
      rarity: editingRecord.rarity,
      description: editingRecord.description,
      status: editingRecord.status,
      ...buildStarImageMap(editingRecord.starImages),
    };
  };

  return (
    <>
      <ProTable<CardTemplateListResponse, Record<string, any>>
        headerTitle="卡牌管理"
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        request={async (params) => {
          const { current, pageSize, name, ipSeriesId, rarity, status } = params;
          const result = await listCardTemplates({
            page: (current ?? 1) - 1,
            size: pageSize,
            name,
            ipSeriesId,
            rarity,
            status: status && status !== 'ALL' ? status : undefined,
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
            className="btn-create-card-template"
            onClick={() => {
              setEditingRecord(null);
              initialStarImagesRef.current = {};
              setModalOpen(true);
            }}
          >
            新建
          </Button>,
        ]}
      />

      {modalOpen && (
        <ModalForm
          title={editingRecord ? '编辑卡牌模板' : '新建卡牌模板'}
          open={modalOpen}
          onOpenChange={(open) => {
            setModalOpen(open);
            if (!open) setEditingRecord(null);
          }}
          initialValues={buildInitialValues()}
          onFinish={handleFinish}
          modalProps={{ destroyOnClose: true, confirmLoading: submitLoading }}
        >
          <ProFormSelect
            name="ipSeriesId"
            label="IP系列"
            placeholder="请选择 IP 系列"
            disabled={!!editingRecord}
            request={async () => {
              const res = await listIpSeries({ status: 'ACTIVE', size: 1000 });
              return (res.content || []).map((item) => ({
                label: item.name,
                value: item.id,
              }));
            }}
            rules={[{ required: true, message: '请选择 IP 系列' }]}
          />
          <ProFormText
            name="code"
            label="编码"
            placeholder="请输入编码"
            rules={[
              { required: true, message: '请输入编码' },
              { min: 2, max: 50, message: '编码长度为 2~50 字符' },
            ]}
          />
          <ProFormText
            name="name"
            label="名称"
            placeholder="请输入名称"
            rules={[
              { required: true, message: '请输入名称' },
              { min: 1, max: 50, message: '名称长度为 1~50 字符' },
            ]}
          />
          <ProFormSelect
            name="rarity"
            label="稀有度"
            placeholder="请选择稀有度"
            valueEnum={Object.fromEntries(
              RARITY_OPTIONS.map((r) => [r, { text: r }]),
            )}
            rules={[{ required: true, message: '请选择稀有度' }]}
          />
          <ProFormTextArea
            name="description"
            label="描述"
            placeholder="请输入描述"
            rules={[{ max: 500, message: '描述不能超过 500 字符' }]}
          />
          <ProFormSelect
            name="status"
            label="状态"
            valueEnum={{ ACTIVE: '启用', INACTIVE: '停用' }}
            rules={[{ required: true, message: '请选择状态' }]}
          />
          <ProForm.Group title="星级图片">
            {[1, 2, 3, 4, 5].map((level) => (
              <ProForm.Item key={level} name={`starImage_${level}`} label={`★${level}`}>
                <StarImageUpload starLevel={level} />
              </ProForm.Item>
            ))}
          </ProForm.Group>
        </ModalForm>
      )}
    </>
  );
};

export default CardTemplate;

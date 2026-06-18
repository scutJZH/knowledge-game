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
} from '@/services/cardTemplate';
import type {
  CardRarity,
  CardTemplateListResponse,
  CardTemplateResponse,
  CardTemplateStatus,
  CreateCardTemplateRequest,
  UpdateCardTemplateRequest,
} from '@/services/cardTemplate';
import { listIpSeries } from '@/services/ipSeries';
import ImageUploadField from '@/components/ImageUploadField';

/**
 * 构造更新负载：按字段对比，未变更字段不发送（undefined 三态）。
 * 必填字段（code/name/rarity/status/ipSeriesId）保持原值，可清空字段（description/imageFileId）
 * 与原值不同则发送新值（含 null=清空）。
 */
function buildUpdatePayload(
  original: CardTemplateResponse,
  values: CardTemplateFormValues,
): UpdateCardTemplateRequest {
  const payload: UpdateCardTemplateRequest = {};

  if (values.code !== original.code) {
    payload.code = values.code;
  }
  if (values.name !== original.name) {
    payload.name = values.name;
  }
  if (values.rarity !== original.rarity) {
    payload.rarity = values.rarity;
  }
  if (values.status !== original.status) {
    payload.status = values.status;
  }
  const nextDesc = values.description || null;
  if (nextDesc !== (original.description || null)) {
    payload.description = nextDesc;
  }
  const nextImage = values.imageFileId ?? null;
  if (nextImage !== (original.imageFileId ?? null)) {
    payload.imageFileId = nextImage;
  }

  return payload;
}

/** 表单值类型（基础字段 + 图片） */
type CardTemplateFormValues = UpdateCardTemplateRequest & {
  ipSeriesId?: number;
};

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

  const columns: ProColumns<CardTemplateListResponse>[] = [
    { title: 'ID', dataIndex: 'id', search: false, width: 80 },
    { title: '编码', dataIndex: 'code', sorter: true },
    { title: '名称', dataIndex: 'name', sorter: true },
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
      sorter: true,
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
      sorter: true,
      render: (_, record) => (
        <Tag color={STATUS_COLOR_MAP[record.status]}>
          {record.status === 'ACTIVE' ? '启用' : '停用'}
        </Tag>
      ),
    },
    { title: '创建时间', dataIndex: 'createdAt', search: false, valueType: 'dateTime', sorter: true },
    { title: '更新时间', dataIndex: 'updatedAt', search: false, valueType: 'dateTime', sorter: true },
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

  /** 打开编辑弹窗：拉取详情 → 预填表单 */
  const handleEdit = async (id: number) => {
    try {
      const detail = await getCardTemplateById(id);
      setEditingRecord(detail);
      setModalOpen(true);
    } catch {
      // 错误已由 request 拦截器展示
    }
  };

  /** 创建/编辑提交 */
  const handleFinish = async (values: CardTemplateFormValues) => {
    setSubmitLoading(true);
    try {
      const { ipSeriesId, ...rest } = values;

      if (editingRecord) {
        const payload = buildUpdatePayload(editingRecord, values);
        await updateCardTemplate(editingRecord.id, payload);
        message.success('更新成功');
      } else {
        await createCardTemplate({ ...rest, ipSeriesId } as CreateCardTemplateRequest);
        message.success('创建成功');
      }

      setModalOpen(false);
      setEditingRecord(null);
      actionRef.current?.reload();
      return true;
    } catch {
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
      imageFileId: editingRecord.imageFileId,
    };
  };

  return (
    <>
      <ProTable<CardTemplateListResponse, Record<string, any>>
        headerTitle="卡牌管理"
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        request={async (params, sort) => {
          const { current, pageSize, name, code, ipSeriesId, rarity, status } = params;
          let sortField: string | undefined;
          let sortOrder: 'asc' | 'desc' | undefined;
          if (sort && typeof sort === 'object' && Object.keys(sort).length > 0) {
            const key = Object.keys(sort)[0];
            sortField = key;
            sortOrder = (sort as Record<string, string>)[key] === 'ascend' ? 'asc' : 'desc';
          }
          const result = await listCardTemplates({
            page: (current ?? 1) - 1,
            size: pageSize,
            name,
            code,
            ipSeriesId,
            rarity,
            status: status && status !== 'ALL' ? status : undefined,
            sort: sortField,
            order: sortOrder,
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
          <ProForm.Item name="imageFileId" label="卡面图">
            <ImageUploadField
              bizType="CARD_TEMPLATE"
              placeholder="上传卡面图"
              url={editingRecord?.imageUrl}
            />
          </ProForm.Item>
        </ModalForm>
      )}
    </>
  );
};

export default CardTemplate;

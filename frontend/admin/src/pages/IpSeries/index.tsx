import React, { useRef, useState } from 'react';
import { Button, Image, message, Popconfirm, Space, Tag } from 'antd';
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
  createIpSeries,
  deleteIpSeries,
  listIpSeries,
  updateIpSeries,
} from '@/services/ipSeries';
import type {
  CreateIpSeriesRequest,
  IpSeriesQuery,
  IpSeriesResponse,
  UpdateIpSeriesRequest,
} from '@/services/ipSeries';
import ImageUploadField from '@/components/ImageUploadField';

/**
 * 构造更新负载：按字段对比，未变更字段不发送（undefined 三态）。
 * 必填字段（code/name/status）保持原值，可清空字段（description/coverImageFileId）
 * 与原值不同则发送新值（含 null=清空）。
 */
function buildUpdatePayload(
  original: IpSeriesResponse,
  values: CreateIpSeriesRequest,
): UpdateIpSeriesRequest {
  const payload: UpdateIpSeriesRequest = {};

  if (values.code !== original.code) {
    payload.code = values.code;
  }
  if (values.name !== original.name) {
    payload.name = values.name;
  }
  if (values.status !== original.status) {
    payload.status = values.status;
  }
  const nextDesc = values.description || null;
  if (nextDesc !== (original.description || null)) {
    payload.description = nextDesc;
  }
  const nextCover = values.coverImageFileId ?? null;
  if (nextCover !== original.coverImageFileId) {
    payload.coverImageFileId = nextCover;
  }

  return payload;
}

/** IP 系列管理页 */
const IpSeries: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [modalOpen, setModalOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<IpSeriesResponse | null>(null);
  const [submitLoading, setSubmitLoading] = useState(false);

  const columns: ProColumns<IpSeriesResponse>[] = [
    { title: 'ID', dataIndex: 'id', search: false, width: 80, sorter: true },
    { title: '编码', dataIndex: 'code', sorter: true },
    { title: '名称', dataIndex: 'name', sorter: true },
    {
      title: '描述',
      dataIndex: 'description',
      search: false,
      ellipsis: true,
    },
    {
      title: '封面图',
      dataIndex: 'coverImageUrl',
      search: false,
      render: (_, record) =>
        record.coverImageUrl ? (
          <Image
            width={48}
            height={48}
            src={record.coverImageUrl}
            style={{ objectFit: 'cover', borderRadius: 4 }}
          />
        ) : (
          <span style={{ color: '#ccc' }}>-</span>
        ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      valueType: 'select',
      initialValue: 'ACTIVE',
      valueEnum: {
        ACTIVE: { text: '启用', status: 'Success' },
        INACTIVE: { text: '停用', status: 'Default' },
      },
      sorter: true,
      render: (_, record) => (
        <Tag color={record.status === 'ACTIVE' ? 'green' : 'default'}>
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
              setEditingRecord(record);
              setModalOpen(true);
            }}
          >
            编辑
          </a>
          <Popconfirm
            title={record.status === 'ACTIVE' ? '确定停用该 IP 系列吗？' : '确定启用该 IP 系列吗？'}
            onConfirm={() => handleToggleStatus(record)}
          >
            {record.status === 'ACTIVE' ? (
              <a>停用</a>
            ) : (
              <a style={{ color: '#52c41a' }}>启用</a>
            )}
          </Popconfirm>
          <Popconfirm
            title="确定删除该 IP 系列吗？删除后将移入回收站。"
            onConfirm={() => handleDelete(record)}
          >
            <a style={{ color: '#ff4d4f' }}>删除</a>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  /** 切换启用/停用状态 */
  const handleToggleStatus = async (record: IpSeriesResponse) => {
    const newStatus = record.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    try {
      await updateIpSeries(record.id, { status: newStatus });
      message.success(newStatus === 'ACTIVE' ? '已启用' : '已停用');
      actionRef.current?.reload();
    } catch (e: any) {
      message.error(e.message || '操作失败');
    }
  };

  /** 删除 */
  const handleDelete = async (record: IpSeriesResponse) => {
    try {
      await deleteIpSeries(record.id);
      message.success('已移入回收站');
      actionRef.current?.reload();
    } catch (e: any) {
      message.error(e.message || '删除失败');
    }
  };

  /** 创建/编辑提交 */
  const handleFinish = async (values: CreateIpSeriesRequest) => {
    setSubmitLoading(true);
    try {
      if (editingRecord) {
        const updateData = buildUpdatePayload(editingRecord, values);
        await updateIpSeries(editingRecord.id, updateData);
        message.success('更新成功');
      } else {
        await createIpSeries(values);
        message.success('创建成功');
      }
      setModalOpen(false);
      setEditingRecord(null);
      actionRef.current?.reload();
      return true;
    } catch (e: any) {
      message.error(e.message || '操作失败');
      return false;
    } finally {
      setSubmitLoading(false);
    }
  };

  return (
    <>
      <ProTable<IpSeriesResponse, IpSeriesQuery>
        headerTitle="IP 系列管理"
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        request={async (params, sort) => {
          const { current, pageSize, name, code, status } = params;
          let sortField: string | undefined;
          let sortOrder: 'asc' | 'desc' | undefined;
          if (sort && typeof sort === 'object' && Object.keys(sort).length > 0) {
            const key = Object.keys(sort)[0];
            sortField = key;
            sortOrder = (sort as Record<string, string>)[key] === 'ascend' ? 'asc' : 'desc';
          }
          const result = await listIpSeries({
            page: (current ?? 1) - 1,
            size: pageSize,
            name,
            code,
            status,
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
            className="btn-create-ip-series"
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
          title={editingRecord ? '编辑 IP 系列' : '新建 IP 系列'}
          open={modalOpen}
          onOpenChange={(open) => {
            setModalOpen(open);
            if (!open) setEditingRecord(null);
          }}
          initialValues={editingRecord ?? { status: 'ACTIVE' }}
          onFinish={handleFinish}
          modalProps={{ destroyOnClose: true, confirmLoading: submitLoading }}
        >
          <ProFormText
            name="code"
            label="编码"
            placeholder="请输入编码"
            rules={[
              { required: true, message: '请输入编码' },
              { min: 2, max: 30, message: '编码长度为 2~30 字符' },
            ]}
          />
          <ProFormText
            name="name"
            label="名称"
            placeholder="请输入名称"
            rules={[
              { required: true, message: '请输入名称' },
              { min: 2, max: 50, message: '名称长度为 2~50 字符' },
            ]}
          />
          <ProFormTextArea
            name="description"
            label="描述"
            placeholder="请输入描述"
            rules={[{ max: 500, message: '描述不能超过 500 字符' }]}
          />
          <ProForm.Item name="coverImageFileId" label="封面图">
            <ImageUploadField
              bizType="IP_SERIES"
              placeholder="上传封面图"
              url={editingRecord?.coverImageUrl}
            />
          </ProForm.Item>
          <ProFormSelect
            name="status"
            label="状态"
            valueEnum={{ ACTIVE: '启用', INACTIVE: '停用' }}
            rules={[{ required: true, message: '请选择状态' }]}
          />
        </ModalForm>
      )}
    </>
  );
};

export default IpSeries;

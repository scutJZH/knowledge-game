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

/** IP 系列管理页 */
const IpSeries: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [modalOpen, setModalOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<IpSeriesResponse | null>(null);
  const [submitLoading, setSubmitLoading] = useState(false);

  const columns: ProColumns<IpSeriesResponse>[] = [
    { title: 'ID', dataIndex: 'id', search: false, width: 80 },
    { title: '编码', dataIndex: 'code', search: false },
    { title: '名称', dataIndex: 'name' },
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
      render: (_, record) => (
        <Tag color={record.status === 'ACTIVE' ? 'green' : 'default'}>
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

  /** 创建/编辑提交 */
  const handleFinish = async (values: CreateIpSeriesRequest | UpdateIpSeriesRequest) => {
    setSubmitLoading(true);
    try {
      if (editingRecord) {
        // 编辑模式下将 undefined 转为 null，确保后端能清空 coverImageUrl
        const updateData = {
          ...values,
          coverImageUrl: values.coverImageUrl ?? null,
        };
        await updateIpSeries(editingRecord.id, updateData as UpdateIpSeriesRequest);
        message.success('更新成功');
      } else {
        await createIpSeries(values as CreateIpSeriesRequest);
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
        request={async (params) => {
          const { current, pageSize, name, status } = params;
          const result = await listIpSeries({
            page: (current ?? 1) - 1,
            size: pageSize,
            name,
            status,
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
          <ProForm.Item name="coverImageUrl" label="封面图">
            <ImageUploadField bizType="IP_SERIES" placeholder="上传封面图" />
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

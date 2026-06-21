import { ActionType, ProColumns, ProTable } from '@ant-design/pro-components';
import { Tag, Tooltip } from 'antd';
import { useRef, useState } from 'react';
import { listTaskLogs, type ScheduledTaskLogItem } from '@/services/scheduledTaskLog';

const statusMap: Record<string, { color: string; text: string }> = {
  SUCCESS: { color: 'green', text: '成功' },
  PARTIAL_FAILURE: { color: 'orange', text: '部分失败' },
  FAILURE: { color: 'red', text: '失败' },
};

function formatDuration(ms: number): string {
  if (ms >= 1000) {
    return `${(ms / 1000).toFixed(1)}s`;
  }
  return `${ms}ms`;
}

const ScheduledTaskLogPage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [taskNameEnum, setTaskNameEnum] = useState<Record<string, string>>({});

  const columns: ProColumns<ScheduledTaskLogItem>[] = [
    {
      title: '任务名称',
      dataIndex: 'taskName',
      valueType: 'select',
      valueEnum: taskNameEnum,
      width: 160,
      render: (_, record) => record.taskDisplay,
    },
    {
      title: '执行时间',
      dataIndex: 'executedAt',
      valueType: 'dateTime',
      width: 180,
      sorter: (a, b) => a.executedAt - b.executedAt,
      defaultSortOrder: 'descend',
    },
    {
      title: '耗时',
      dataIndex: 'durationMs',
      width: 100,
      render: (_, record) => formatDuration(record.durationMs),
    },
    {
      title: '过期总数',
      dataIndex: 'totalCount',
      width: 100,
    },
    {
      title: '成功',
      dataIndex: 'successCount',
      width: 80,
      render: (_, record) => (
        <span style={{ color: '#52c41a' }}>{record.successCount}</span>
      ),
    },
    {
      title: '失败',
      dataIndex: 'failureCount',
      width: 80,
      render: (_, record) => {
        if (record.failureCount > 0 && record.failureDetails?.length) {
          const detailList = record.failureDetails.map(
            (f) => `[${f.resourceType}] ${f.name}: ${f.reason}`,
          );
          return (
            <Tooltip
              title={detailList.map((d, i) => <div key={i}>{d}</div>)}
            >
              <span style={{ color: '#ff4d4f', cursor: 'pointer' }}>
                {record.failureCount}
              </span>
            </Tooltip>
          );
        }
        return <span>{record.failureCount}</span>;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => {
        const cfg = statusMap[record.status] || { color: 'default', text: record.status };
        return <Tag color={cfg.color}>{cfg.text}</Tag>;
      },
    },
  ];

  return (
    <ProTable<ScheduledTaskLogItem>
      columns={columns}
      request={async (params) => {
        const { current, pageSize, taskName } = params as Record<string, any>;
        const res = await listTaskLogs({
          taskName,
          page: current ? current - 1 : 0,
          size: pageSize ?? 20,
        });
        // 从已加载数据中按 taskName 去重提取 filter 下拉选项
        if (res.data?.content) {
          const enumMap: Record<string, string> = {};
          const seen = new Set<string>();
          res.data.content.forEach((item: ScheduledTaskLogItem) => {
            if (!seen.has(item.taskName)) {
              seen.add(item.taskName);
              enumMap[item.taskName] = item.taskDisplay;
            }
          });
          setTaskNameEnum(enumMap);
        }
        return {
          data: res.data?.content || [],
          total: res.data?.totalElements || 0,
          success: res.code === 200,
        };
      }}
      rowKey="id"
      actionRef={actionRef}
      search={false}
      toolbar={{
        title: '定时任务日志',
      }}
      pagination={{
        defaultPageSize: 20,
        showSizeChanger: true,
      }}
    />
  );
};

export default ScheduledTaskLogPage;

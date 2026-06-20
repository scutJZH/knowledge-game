import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

/** 模拟 antd message */
jest.mock('antd', () => {
  const actual = jest.requireActual('antd');
  return {
    ...actual,
    message: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
  };
});

/** 模拟服务层 */
jest.mock('@/services/recycleBin', () => ({
  ...jest.requireActual('@/services/recycleBin'),
  restoreItem: jest.fn(),
  batchRestoreItems: jest.fn(),
}));

import { message } from 'antd';
import { restoreItem, batchRestoreItems } from '@/services/recycleBin';
import RecycleBinTable from '../RecycleBinTable';

/** 构造模拟回收站列表项 */
function mockItem(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    resourceType: 'IP_SERIES',
    resourceTypeDisplay: 'IP 系列',
    originalId: 100,
    originalName: '测试资源',
    originalCreatedAt: 1718000000000,
    originalUpdatedAt: 1718100000000,
    originalCreatedBy: 'admin',
    originalUpdatedBy: null,
    deletedBy: 'admin',
    deletedAt: 1720000000000,
    restoreDeadline: 1722592000000,
    daysUntilPurge: 23,
    ...overrides,
  };
}

const onRestore = jest.fn();
const onBatchRestore = jest.fn();

const defaultProps = {
  dataSource: [mockItem(), mockItem({ id: 2, originalName: '第二个资源' })],
  loading: false,
  total: 2,
  pagination: { current: 1, pageSize: 20 },
  onPaginationChange: jest.fn(),
  onSearch: jest.fn(),
  onSort: jest.fn(),
  selectedRowKeys: [],
  onSelectChange: jest.fn(),
  onRestore,
  onBatchRestore,
};

beforeEach(() => {
  jest.clearAllMocks();
});

describe('RecycleBinTable — 行内恢复', () => {
  it('行内「恢复」按钮存在且 enabled', () => {
    render(<RecycleBinTable {...defaultProps} />);

    // 每行一个恢复按钮，2 行共 2 个
    const buttons = screen.getAllByText('恢复');
    expect(buttons).toHaveLength(2);
    buttons.forEach((btn) => {
      expect(btn.closest('button')).not.toBeDisabled();
    });
  });

  it('点击「恢复」→ Popconfirm 弹出 + 文案正确', async () => {
    render(<RecycleBinTable {...defaultProps} />);

    const buttons = screen.getAllByText('恢复');
    await userEvent.click(buttons[0]);

    // Popconfirm 内容渲染到 document.body
    await waitFor(() => {
      expect(screen.getByText('恢复该条目？')).toBeInTheDocument();
    });
    expect(screen.getByText('恢复后将以「停用」状态回到原列表，需手动启用。')).toBeInTheDocument();
  });

  it('Popconfirm 确认 → 调用 onRestore + message.success + 刷新', async () => {
    // 模拟 index.tsx 的 handleRestore 行为
    const handleRestore = jest.fn(async (_id: number) => {
      await restoreItem(_id);
      (message.success as jest.Mock)('恢复成功，已回到原列表（停用状态）');
    });
    (restoreItem as jest.Mock).mockResolvedValueOnce(undefined);

    render(<RecycleBinTable {...defaultProps} onRestore={handleRestore} />);

    const buttons = screen.getAllByText('恢复');
    await userEvent.click(buttons[0]);

    await waitFor(() => {
      expect(screen.getByText('恢复该条目？')).toBeInTheDocument();
    });

    // Popconfirm 确认按钮在 .ant-popconfirm 容器内（portal 到 document.body）
    const confirmBtn = document.querySelector('.ant-popconfirm .ant-btn-primary')!;
    await userEvent.click(confirmBtn);

    await waitFor(() => {
      expect(restoreItem).toHaveBeenCalledWith(1);
      expect(message.success).toHaveBeenCalledWith('恢复成功，已回到原列表（停用状态）');
    });
  });

  it('单条恢复失败 → 调用 message.error', async () => {
    // 模拟 index.tsx 的 handleRestore 行为：API reject → message.error
    const errorMsg = '资源类型 IP_SERIES 暂未接入回收站';
    const handleRestore = jest.fn(async (_id: number) => {
      try {
        await restoreItem(_id);
        (message.success as jest.Mock)('恢复成功');
      } catch (e: any) {
        (message.error as jest.Mock)(e?.message || '恢复失败');
      }
    });
    (restoreItem as jest.Mock).mockRejectedValueOnce(new Error(errorMsg));

    render(<RecycleBinTable {...defaultProps} onRestore={handleRestore} />);

    const buttons = screen.getAllByText('恢复');
    await userEvent.click(buttons[0]);

    await waitFor(() => {
      expect(screen.getByText('恢复该条目？')).toBeInTheDocument();
    });

    const confirmBtn = document.querySelector('.ant-popconfirm .ant-btn-primary')!;
    await userEvent.click(confirmBtn);

    await waitFor(() => {
      expect(message.error).toHaveBeenCalledWith(errorMsg);
    });
  });
});

describe('RecycleBinTable — 批量恢复', () => {
  it('批量按钮未选中 disabled', () => {
    render(<RecycleBinTable {...defaultProps} selectedRowKeys={[]} />);

    const btn = screen.queryByText('批量恢复');
    // tableAlertOption 在未选中时不渲染
    expect(btn).toBeNull();
  });

  it('批量按钮选中启用 + Popconfirm 标题含数量', async () => {
    render(<RecycleBinTable {...defaultProps} selectedRowKeys={[1, 2]} />);

    const btn = screen.getByText('批量恢复');
    expect(btn.closest('button')).not.toBeDisabled();

    await userEvent.click(btn);

    await waitFor(() => {
      expect(screen.getByText('批量恢复选中的 2 条？')).toBeInTheDocument();
    });
  });

  it('批量全成功 → message.success 含数量', async () => {
    const handleBatchRestore = jest.fn(async () => {
      const ids = [1, 2];
      const result = await batchRestoreItems(ids);
      (message.success as jest.Mock)(`成功恢复 ${result.successIds.length} 条`);
    });
    (batchRestoreItems as jest.Mock).mockResolvedValueOnce({
      successIds: [1, 2],
      failures: [],
    });

    render(
      <RecycleBinTable
        {...defaultProps}
        selectedRowKeys={[1, 2]}
        onBatchRestore={handleBatchRestore}
      />,
    );

    const btn = screen.getByText('批量恢复');
    await userEvent.click(btn);

    await waitFor(() => {
      expect(screen.getByText('批量恢复选中的 2 条？')).toBeInTheDocument();
    });

    const confirmBtn = document.querySelector('.ant-popconfirm .ant-btn-primary')!;
    await userEvent.click(confirmBtn);

    await waitFor(() => {
      expect(message.success).toHaveBeenCalledWith('成功恢复 2 条');
    });
  });

  it('批量部分成功 → message.warning', async () => {
    const handleBatchRestore = jest.fn(async () => {
      const ids = [1, 2];
      const result = await batchRestoreItems(ids);
      const { successIds, failures } = result;
      if (successIds.length > 0 && failures.length > 0) {
        (message.warning as jest.Mock)(`成功 ${successIds.length} 条，失败 ${failures.length} 条`);
      }
    });
    (batchRestoreItems as jest.Mock).mockResolvedValueOnce({
      successIds: [1],
      failures: [{ id: 2, errorMessage: '资源类型 X 暂未接入回收站' }],
    });

    render(
      <RecycleBinTable
        {...defaultProps}
        selectedRowKeys={[1, 2]}
        onBatchRestore={handleBatchRestore}
      />,
    );

    const btn = screen.getByText('批量恢复');
    await userEvent.click(btn);

    await waitFor(() => {
      expect(screen.getByText('批量恢复选中的 2 条？')).toBeInTheDocument();
    });

    const confirmBtn = document.querySelector('.ant-popconfirm .ant-btn-primary')!;
    await userEvent.click(confirmBtn);

    await waitFor(() => {
      expect(message.warning).toHaveBeenCalledWith('成功 1 条，失败 1 条');
    });
  });

  it('批量全失败 → message.error 含首条 errorMessage', async () => {
    const handleBatchRestore = jest.fn(async () => {
      const ids = [1, 2];
      const result = await batchRestoreItems(ids);
      const { failures } = result;
      if (failures.length > 0 && result.successIds.length === 0) {
        (message.error as jest.Mock)(failures[0]?.errorMessage || '批量恢复失败');
      }
    });
    (batchRestoreItems as jest.Mock).mockResolvedValueOnce({
      successIds: [],
      failures: [
        { id: 1, errorMessage: '资源类型 IP_SERIES 暂未接入回收站' },
        { id: 2, errorMessage: '回收站记录不存在' },
      ],
    });

    render(
      <RecycleBinTable
        {...defaultProps}
        selectedRowKeys={[1, 2]}
        onBatchRestore={handleBatchRestore}
      />,
    );

    const btn = screen.getByText('批量恢复');
    await userEvent.click(btn);

    await waitFor(() => {
      expect(screen.getByText('批量恢复选中的 2 条？')).toBeInTheDocument();
    });

    const confirmBtn = document.querySelector('.ant-popconfirm .ant-btn-primary')!;
    await userEvent.click(confirmBtn);

    await waitFor(() => {
      expect(message.error).toHaveBeenCalledWith('资源类型 IP_SERIES 暂未接入回收站');
    });
  });
});

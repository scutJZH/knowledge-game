import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import IpLibraryTab from '../IpLibraryTab';

const {
  mockListGroupIpLibrary,
  mockUpdateGroupIpLibrary,
  mockListActiveIpSeries,
} = vi.hoisted(() => ({
  mockListGroupIpLibrary: vi.fn(),
  mockUpdateGroupIpLibrary: vi.fn(),
  mockListActiveIpSeries: vi.fn(),
}));

vi.mock('@/services/group-api', () => ({
  listGroupIpLibrary: mockListGroupIpLibrary,
  updateGroupIpLibrary: mockUpdateGroupIpLibrary,
  listActiveIpSeries: mockListActiveIpSeries,
}));

vi.mock('antd', async () => {
  const actual = await vi.importActual<typeof import('antd')>('antd');
  return { ...actual };
});

const ACTIVE_IP_LIST = [
  { id: 1, name: '宝可梦', code: 'PKM', coverImageFileId: 100, coverImageUrl: 'https://example.com/pkm.png' },
  { id: 2, name: '数码宝贝', code: 'DM', coverImageFileId: null, coverImageUrl: null },
];

const LINKED_IP_LIST = [
  { id: 1, groupId: 1, ipSeriesId: 1, ipSeriesName: '宝可梦', ipSeriesCode: 'PKM', coverImageFileId: 100, coverImageUrl: 'https://example.com/pkm.png', addedAt: 1718800000000 },
];

function setup(myRole: 'OWNER' | 'ADMIN' | 'MEMBER' = 'OWNER') {
  mockListGroupIpLibrary.mockResolvedValue(LINKED_IP_LIST);
  mockListActiveIpSeries.mockResolvedValue(ACTIVE_IP_LIST);
  mockUpdateGroupIpLibrary.mockResolvedValue([
    { ipSeriesId: 1 }, { ipSeriesId: 2 },
  ]);
  return render(<IpLibraryTab groupId={1} myRole={myRole} />);
}

describe('IpLibraryTab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('OWNER 渲染时已关联 IP 的 Checkbox 选中且可点击', async () => {
    setup('OWNER');
    await waitFor(() => expect(screen.queryByText('宝可梦')).toBeInTheDocument());

    const checkbox = screen.getByTestId('ip-card-1').querySelector('input[type="checkbox"]') as HTMLInputElement;
    expect(checkbox.checked).toBe(true);
    expect(checkbox.disabled).toBe(false);
  });

  it('ADMIN 渲染时 Checkbox 可点击且保存按钮可见', async () => {
    setup('ADMIN');
    await waitFor(() => expect(screen.queryByText('宝可梦')).toBeInTheDocument());

    expect(screen.getByText('保存修改')).toBeInTheDocument();
    const checkbox = screen.getByTestId('ip-card-1').querySelector('input[type="checkbox"]') as HTMLInputElement;
    expect(checkbox.disabled).toBe(false);
  });

  it('MEMBER 渲染时 Checkbox disabled 且不渲染保存按钮', async () => {
    setup('MEMBER');
    await waitFor(() => expect(screen.queryByText('宝可梦')).toBeInTheDocument());

    const checkbox = screen.getByTestId('ip-card-1').querySelector('input[type="checkbox"]') as HTMLInputElement;
    expect(checkbox.disabled).toBe(true);
    expect(screen.queryByText('保存修改')).not.toBeInTheDocument();
  });

  it('勾选新 IP 后保存按钮可用，点击后 PUT 调用参数包含新 ID 并用响应重置', async () => {
    const user = userEvent.setup();
    setup('OWNER');
    await waitFor(() => expect(screen.queryByText('数码宝贝')).toBeInTheDocument());

    await user.click(screen.getByTestId('ip-card-2'));
    await waitFor(() => expect(screen.getByRole('button', { name: '保存修改' })).not.toBeDisabled());

    await user.click(screen.getByRole('button', { name: '保存修改' }));
    await waitFor(() => {
      expect(mockUpdateGroupIpLibrary).toHaveBeenCalledWith(1, [1, 2]);
    });
  });

  it('取消已勾选的 IP 后保存，PUT 调用参数排除该 ID', async () => {
    const user = userEvent.setup();
    setup('OWNER');
    await waitFor(() => expect(screen.queryByText('宝可梦')).toBeInTheDocument());

    await user.click(screen.getByTestId('ip-card-1'));
    await waitFor(() => expect(screen.getByRole('button', { name: '保存修改' })).not.toBeDisabled());

    await user.click(screen.getByRole('button', { name: '保存修改' }));
    await waitFor(() => {
      expect(mockUpdateGroupIpLibrary).toHaveBeenCalledWith(1, []);
    });
  });

  it('已关联列表含孤儿 ID（已停用）时自动过滤，不计入选中等，PUT 不含孤儿 ID', async () => {
    const user = userEvent.setup();
    mockListGroupIpLibrary.mockResolvedValue([
      { ipSeriesId: 1 }, { ipSeriesId: 99 },
    ]);
    mockListActiveIpSeries.mockResolvedValue(ACTIVE_IP_LIST);
    render(<IpLibraryTab groupId={1} myRole="OWNER" />);

    await waitFor(() => expect(screen.queryByText('宝可梦')).toBeInTheDocument());

    // 孤儿 ID=99 不渲染卡片
    expect(screen.queryByTestId('ip-card-99')).not.toBeInTheDocument();
    // 工具栏：已选 1（孤儿不计入）/ 共 2 项
    expect(screen.getByText(/已选 1 \/ 共 2 项/)).toBeInTheDocument();
    // 保存按钮 disabled（无修改）
    expect(screen.getByRole('button', { name: '保存修改' })).toBeDisabled();

    // 勾选 IP2 后保存 → PUT 参数为 [1, 2] 而非 [1, 99, 2]
    await user.click(screen.getByTestId('ip-card-2'));
    await waitFor(() => expect(screen.getByRole('button', { name: '保存修改' })).not.toBeDisabled());
    await user.click(screen.getByRole('button', { name: '保存修改' }));
    await waitFor(() => {
      expect(mockUpdateGroupIpLibrary).toHaveBeenCalledWith(1, [1, 2]);
    });
  });

  it('无修改时保存按钮 disabled', async () => {
    setup('OWNER');
    await waitFor(() => expect(screen.queryByText('宝可梦')).toBeInTheDocument());

    expect(screen.getByRole('button', { name: '保存修改' })).toBeDisabled();
  });

  it('加载失败时渲染 Result error', async () => {
    mockListGroupIpLibrary.mockRejectedValue({ response: { data: { message: '网络错误' } } });
    mockListActiveIpSeries.mockResolvedValue(ACTIVE_IP_LIST);
    render(<IpLibraryTab groupId={1} myRole="OWNER" />);

    await waitFor(() => expect(screen.getByText('网络错误')).toBeInTheDocument());
  });

  it('可选 IP 列表为空时渲染空状态文案', async () => {
    mockListGroupIpLibrary.mockResolvedValue([]);
    mockListActiveIpSeries.mockResolvedValue([]);
    render(<IpLibraryTab groupId={1} myRole="OWNER" />);

    await waitFor(() => expect(screen.getByText('系统中暂无可用 IP 系列')).toBeInTheDocument());
  });

  it('封面图 url 为 null 时渲染首字占位符', async () => {
    setup('OWNER');
    await waitFor(() => expect(screen.queryByText('数码宝贝')).toBeInTheDocument());

    const card = screen.getByTestId('ip-card-2');
    const placeholder = card.querySelector('.avatar-placeholder');
    expect(placeholder).toBeInTheDocument();
    expect(placeholder?.textContent).toBe('数');
  });
});

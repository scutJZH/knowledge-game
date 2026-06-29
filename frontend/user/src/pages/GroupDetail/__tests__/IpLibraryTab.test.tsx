import { render, screen, waitFor, within } from '@testing-library/react';
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

const LINKED = [
  { id: 1, groupId: 1, ipSeriesId: 1, ipSeriesName: '宝可梦', ipSeriesCode: 'PKM', coverImageFileId: 100, coverImageUrl: 'https://example.com/pkm.png', status: 'ACTIVE' as const, addedAt: 1 },
  { id: 2, groupId: 1, ipSeriesId: 2, ipSeriesName: '数码宝贝', ipSeriesCode: 'DM', coverImageFileId: null, coverImageUrl: null, status: 'ACTIVE' as const, addedAt: 2 },
];

const ALL_ACTIVE = [
  { id: 1, name: '宝可梦', code: 'PKM', coverImageFileId: 100, coverImageUrl: 'https://example.com/pkm.png' },
  { id: 2, name: '数码宝贝', code: 'DM', coverImageFileId: null, coverImageUrl: null },
  { id: 3, name: '原神', code: 'GENSHIN', coverImageFileId: null, coverImageUrl: null },
];

function setup(myRole: 'OWNER' | 'ADMIN' | 'MEMBER' = 'OWNER') {
  mockListGroupIpLibrary.mockResolvedValue(LINKED);
  mockListActiveIpSeries.mockResolvedValue(ALL_ACTIVE);
  mockUpdateGroupIpLibrary.mockResolvedValue(LINKED);
  return render(<IpLibraryTab groupId={1} myRole={myRole} />);
}

describe('IpLibraryTab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('渲染已关联 IP 卡片网格', async () => {
    setup();
    await waitFor(() => expect(screen.getByTestId('ip-card-1')).toBeInTheDocument());
    expect(screen.getByTestId('ip-card-1')).toHaveTextContent('宝可梦');
    expect(screen.getByTestId('ip-card-2')).toHaveTextContent('数码宝贝');
    expect(screen.getByText('已关联 2 项')).toBeInTheDocument();
  });

  it('OWNER 看到添加按钮和卡片 ⋮ 菜单', async () => {
    setup('OWNER');
    await waitFor(() => expect(screen.getByTestId('ip-card-1')).toBeInTheDocument());
    expect(screen.getByText('添加 IP 系列')).toBeInTheDocument();
    expect(screen.getByTestId('ip-card-menu-1')).toBeInTheDocument();
  });

  it('MEMBER 看不到添加按钮和 ⋮ 菜单', async () => {
    setup('MEMBER');
    await waitFor(() => expect(screen.getByTestId('ip-card-1')).toBeInTheDocument());
    expect(screen.queryByText('添加 IP 系列')).not.toBeInTheDocument();
    expect(screen.queryByTestId('ip-card-menu-1')).not.toBeInTheDocument();
  });

  it('弹出添加弹窗 → 搜索过滤 → 多选确定', async () => {
    const user = userEvent.setup();
    mockUpdateGroupIpLibrary.mockResolvedValue([...LINKED, { id: 3, groupId: 1, ipSeriesId: 3, ipSeriesName: '原神', ipSeriesCode: 'GENSHIN', coverImageFileId: null, coverImageUrl: null, addedAt: 3 }]);
    setup();

    await waitFor(() => expect(screen.getByRole('button', { name: /添加/ })).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /添加/ }));

    // 弹窗出现，可选列表不含已关联 IP
    await waitFor(() => expect(screen.getByText('原神')).toBeInTheDocument());
    // 弹窗内不应该有已关联的 IP
    const modal = document.querySelector('.ant-modal-body')!;
    expect(within(modal).queryByText('宝可梦')).not.toBeInTheDocument();

    // 搜索过滤
    const searchInput = screen.getByPlaceholderText('搜索 IP 名称或编码');
    await user.type(searchInput, '数码');
    expect(within(modal).queryByText('原神')).not.toBeInTheDocument();
    await user.clear(searchInput);
    expect(within(modal).getByText('原神')).toBeInTheDocument();

    // 勾选 → 确定
    await user.click(within(modal).getByText('原神'));
    await user.click(screen.getByRole('button', { name: /确\s*定/ }));

    await waitFor(() => {
      expect(mockUpdateGroupIpLibrary).toHaveBeenCalledWith(1, [1, 2, 3]);
    });
  });

  it('⋯ 菜单删除 → PUT 不含该 ID', async () => {
    const user = userEvent.setup();
    mockUpdateGroupIpLibrary.mockResolvedValue([LINKED[1]]);
    setup();

    await waitFor(() => expect(screen.getByTestId('ip-card-menu-1')).toBeInTheDocument());
    await user.click(screen.getByTestId('ip-card-menu-1'));

    await waitFor(() => expect(screen.getByText('删除')).toBeInTheDocument());
    await user.click(screen.getByText('删除'));

    await waitFor(() => {
      expect(mockUpdateGroupIpLibrary).toHaveBeenCalledWith(1, [2]);
    });
  });

  it('空关联时显示空状态文案', async () => {
    mockListGroupIpLibrary.mockResolvedValue([]);
    mockListActiveIpSeries.mockResolvedValue(ALL_ACTIVE);
    render(<IpLibraryTab groupId={1} myRole="OWNER" />);

    await waitFor(() => expect(screen.getByText(/暂未关联/)).toBeInTheDocument());
  });

  it('加载失败时渲染 Result error + 重试', async () => {
    mockListGroupIpLibrary.mockRejectedValue({ response: { data: { message: '网络错误' } } });
    mockListActiveIpSeries.mockResolvedValue(ALL_ACTIVE);
    render(<IpLibraryTab groupId={1} myRole="OWNER" />);

    await waitFor(() => expect(screen.getByText('网络错误')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: /重\s*试/ })).toBeInTheDocument();
  });

  it('封面图为空时渲染首字占位符', async () => {
    setup();
    await waitFor(() => expect(screen.getByTestId('ip-card-2')).toBeInTheDocument());
    const card = screen.getByTestId('ip-card-2');
    const placeholder = within(card).getByText('数');
    expect(placeholder.closest('.avatar-placeholder')).toBeInTheDocument();
  });
});

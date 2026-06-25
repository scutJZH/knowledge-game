import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import KnowledgeTab from '../KnowledgeTab';

const mockGet = vi.hoisted(() => vi.fn());
vi.mock('@/services/api-client', () => ({
  apiClient: { get: mockGet },
}));
vi.mock('@/services/group-api', () => ({
  listQuestionsByCategory: vi.fn().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0 }),
}));

describe('KnowledgeTab', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('加载中显示 Spin', () => {
    mockGet.mockReturnValue(new Promise(() => {}));
    render(<KnowledgeTab />);
    expect(document.querySelector('.ant-spin')).toBeInTheDocument();
  });

  it('分类加载失败提示 error', async () => {
    mockGet.mockRejectedValue(new Error('fail'));
    render(<KnowledgeTab />);
    await waitFor(() => { expect(screen.getByText('暂无分类')).toBeInTheDocument(); }, { timeout: 5000 });
  });

  it('渲染分类卡片', async () => {
    mockGet.mockResolvedValue([{ id: 1, name: '历史' }, { id: 2, name: '地理', children: [{ id: 3, name: '中国地理' }] }]);
    render(<KnowledgeTab />);
    await waitFor(() => { expect(screen.getByText('历史')).toBeInTheDocument(); });
    expect(screen.getByText('中国地理')).toBeInTheDocument();
  });

  it('点击分类进入题目列表', async () => {
    mockGet.mockResolvedValue([{ id: 1, name: '历史' }]);
    const mockList = await import('@/services/group-api');
    (mockList.listQuestionsByCategory as ReturnType<typeof vi.fn>).mockResolvedValue({
      content: [{ id: 1, title: '问题1', fullText: '完整', answer: '答案', difficulty: 1, type: 'SINGLE_CHOICE', createdAt: 1 }],
      totalElements: 1, totalPages: 1,
    });
    render(<KnowledgeTab />);
    await waitFor(() => { expect(screen.getByText('历史')).toBeInTheDocument(); });
  });
});

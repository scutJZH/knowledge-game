import { render, screen } from '@testing-library/react';
import ImportResultModal from '../components/ImportResultModal';
import type { KnowledgeItemImportResult } from '@/services/knowledge-item';

describe('ImportResultModal', () => {
  it('renders import result with total/success/fail counts', () => {
    const result: KnowledgeItemImportResult = {
      totalCount: 10,
      successCount: 7,
      failCount: 3,
      failDetails: [
        { row: 2, reason: '标题不能为空' },
        { row: 5, reason: '分类名称不存在或已停用：不存在分类' },
        { row: 8, reason: '状态格式错误：未知，可选值 启用/停用' },
      ],
    };

    render(
      <ImportResultModal
        open={true}
        result={result}
        onClose={jest.fn()}
      />,
    );

    expect(screen.getByText('10')).toBeInTheDocument();
    expect(screen.getByText('7')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
    // 失败明细 Table 应渲染
    expect(screen.getByText('标题不能为空')).toBeInTheDocument();
    expect(screen.getByText('分类名称不存在或已停用：不存在分类')).toBeInTheDocument();
  });

  it('does not render fail details table when failCount is 0', () => {
    const result: KnowledgeItemImportResult = {
      totalCount: 5,
      successCount: 5,
      failCount: 0,
      failDetails: [],
    };

    render(
      <ImportResultModal
        open={true}
        result={result}
        onClose={jest.fn()}
      />,
    );

    // 总行数和成功数都是 5（值相同），用 getAllByText 验证
    const fives = screen.getAllByText('5');
    expect(fives.length).toBeGreaterThanOrEqual(2);
    // 不应渲染失败明细 Table 或 Alert
    expect(screen.queryByText('请仅修正失败行后重新上传')).not.toBeInTheDocument();
  });

  it('returns null when result is null', () => {
    const { container } = render(
      <ImportResultModal
        open={false}
        result={null}
        onClose={jest.fn()}
      />,
    );

    expect(container.innerHTML).toBe('');
  });
});

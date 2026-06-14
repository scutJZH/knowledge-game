import React from 'react';
import { render, screen } from '@testing-library/react';
import ImportResultModal from '../ImportResultModal';
import type { QuestionImportResult } from '@/services/questionBank';

/** 构造模拟导入结果 */
function buildResult(overrides?: Partial<QuestionImportResult>): QuestionImportResult {
  return {
    totalCount: 5,
    successCount: 4,
    failCount: 1,
    failDetails: [{ row: 3, reason: '题型不能为空' }],
    ...overrides,
  };
}

describe('ImportResultModal', () => {
  it('应显示统计数字', () => {
    render(
      <ImportResultModal
        open={true}
        result={buildResult()}
        onClose={jest.fn()}
      />,
    );
    expect(screen.getByText('5')).toBeInTheDocument();
    expect(screen.getByText('4')).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument();
  });

  it('失败数 > 0 时应显示失败明细表格', () => {
    render(
      <ImportResultModal
        open={true}
        result={buildResult()}
        onClose={jest.fn()}
      />,
    );
    expect(screen.getByText('行号')).toBeInTheDocument();
    expect(screen.getByText('失败原因')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('题型不能为空')).toBeInTheDocument();
  });

  it('失败数 > 0 时应显示重复导入警告', () => {
    render(
      <ImportResultModal
        open={true}
        result={buildResult()}
        onClose={jest.fn()}
      />,
    );
    expect(
      screen.getByText(/请仅修正失败行后重新上传/),
    ).toBeInTheDocument();
  });

  it('全部成功时不应显示失败明细', () => {
    render(
      <ImportResultModal
        open={true}
        result={buildResult({ failCount: 0, failDetails: [] })}
        onClose={jest.fn()}
      />,
    );
    expect(screen.queryByText('行号')).not.toBeInTheDocument();
    expect(screen.queryByText(/请仅修正失败行/)).not.toBeInTheDocument();
  });

  it('result 为 null 时应渲染 null', () => {
    const { container } = render(
      <ImportResultModal open={true} result={null} onClose={jest.fn()} />,
    );
    expect(container.innerHTML).toBe('');
  });

  it('关闭按钮点击应触发 onClose', () => {
    const onClose = jest.fn();
    render(
      <ImportResultModal
        open={true}
        result={buildResult()}
        onClose={onClose}
      />,
    );
    // 点击 Modal 的确定按钮（AntD Modal okButton）
    const modalFooter = document.querySelector('.ant-modal-footer');
    const okButton = modalFooter?.querySelector('.ant-btn-primary');
    expect(okButton).toBeInTheDocument();
    (okButton as HTMLButtonElement).click();
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});

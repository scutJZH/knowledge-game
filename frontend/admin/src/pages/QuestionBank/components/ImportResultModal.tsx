import React from 'react';
import { Alert, Modal, Table } from 'antd';
import type { QuestionImportResult } from '@/services/questionBank';

/** 导入结果 Modal Props */
export interface ImportResultModalProps {
  open: boolean;
  result: QuestionImportResult | null;
  onClose: () => void;
}

/** 失败明细表格列定义 */
const FAIL_COLUMNS = [
  { title: '行号', dataIndex: 'row', width: 80 },
  { title: '失败原因', dataIndex: 'reason' },
];

/** 导入结果展示 Modal */
const ImportResultModal: React.FC<ImportResultModalProps> = ({
  open,
  result,
  onClose,
}) => {
  if (!result) return null;

  return (
    <Modal
      title="导入结果"
      open={open}
      onCancel={onClose}
      onOk={onClose}
      cancelButtonProps={{ style: { display: 'none' } }}
      width={600}
    >
      {/* 统计数字 */}
      <div style={{ marginBottom: 16 }}>
        <span style={{ marginRight: 24 }}>
          总行数：<strong>{result.totalCount}</strong>
        </span>
        <span style={{ marginRight: 24, color: '#52c41a' }}>
          成功：<strong>{result.successCount}</strong>
        </span>
        <span style={{ color: '#ff4d4f' }}>
          失败：<strong>{result.failCount}</strong>
        </span>
      </div>

      {/* 失败明细 */}
      {result.failCount > 0 && (
        <>
          <Alert
            type="warning"
            showIcon
            message="请仅修正失败行后重新上传，重复导入会创建重复题目"
            style={{ marginBottom: 16 }}
          />
          <Table
            dataSource={result.failDetails.map((item, idx) => ({
              ...item,
              key: idx,
            }))}
            columns={FAIL_COLUMNS}
            pagination={false}
            size="small"
            bordered
          />
        </>
      )}
    </Modal>
  );
};

export default ImportResultModal;

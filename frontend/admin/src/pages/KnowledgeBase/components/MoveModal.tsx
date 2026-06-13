import { useState, useMemo } from 'react';
import { Modal, TreeSelect, message } from 'antd';
import type { CategoryTreeNode } from '@/services/knowledge-category';
import { move } from '@/services/knowledge-category';

interface MoveModalProps {
  visible: boolean;
  categoryId: number | null;
  treeData: CategoryTreeNode[];
  onSuccess: () => void;
  onCancel: () => void;
}

/** 将分类树转为 TreeSelect 格式，排除指定节点及其后代 */
function toTreeSelectExclude(
  nodes: CategoryTreeNode[],
  excludeId: number,
): { title: string; value: number; children?: any[] }[] {
  return nodes
    .filter((n) => n.id !== excludeId)
    .map((node) => ({
      title: node.name,
      value: node.id,
      children: node.children
        ? toTreeSelectExclude(node.children, excludeId)
        : undefined,
    }));
}

const MoveModal: React.FC<MoveModalProps> = ({
  visible,
  categoryId,
  treeData,
  onSuccess,
  onCancel,
}) => {
  const [targetParentId, setTargetParentId] = useState<number | undefined>(undefined);
  const [loading, setLoading] = useState(false);

  // 过滤掉当前节点及其后代，避免循环引用
  const treeSelectData = useMemo(() => {
    if (!categoryId) return [];
    return toTreeSelectExclude(treeData, categoryId);
  }, [treeData, categoryId]);

  /** 确认移动 */
  const handleOk = async () => {
    if (!categoryId) return;
    setLoading(true);
    try {
      await move(categoryId, targetParentId ?? null);
      message.success('移动成功');
      setTargetParentId(undefined);
      onSuccess();
    } catch {
      // 错误已由全局拦截器处理
    } finally {
      setLoading(false);
    }
  };

  /** 取消 */
  const handleCancel = () => {
    setTargetParentId(undefined);
    onCancel();
  };

  return (
    <Modal
      title="移动分类"
      open={visible}
      onOk={handleOk}
      onCancel={handleCancel}
      confirmLoading={loading}
      okText="确认移动"
      cancelText="取消"
      destroyOnClose
    >
      <div style={{ marginBottom: 16, color: '#666' }}>
        选择目标父级分类，不选则移动到顶级。
      </div>
      <TreeSelect
        value={targetParentId}
        onChange={setTargetParentId}
        treeData={treeSelectData}
        placeholder="不选则移动到顶级"
        allowClear
        showSearch
        treeNodeFilterProp="title"
        treeDefaultExpandAll
        style={{ width: '100%' }}
      />
    </Modal>
  );
};

export default MoveModal;

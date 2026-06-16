import { useState, useCallback, useEffect } from 'react';
import { Row, Col, message } from 'antd';
import type { CategoryTreeNode, CategoryDetail } from '@/services/knowledge-category';
import { getTree, getById, deleteCategory } from '@/services/knowledge-category';
import CategoryTree from './components/CategoryTree';
import CategoryDetailPanel from './components/CategoryDetail';
import CategoryFormModal from './components/CategoryFormModal';
import MoveModal from './components/MoveModal';

const KnowledgeBase: React.FC = () => {
  // 分类树数据
  const [treeData, setTreeData] = useState<CategoryTreeNode[]>([]);
  const [loading, setLoading] = useState(false);

  // 当前选中的分类
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<CategoryDetail | null>(null);

  // 弹窗状态
  const [formVisible, setFormVisible] = useState(false);
  const [editingCategory, setEditingCategory] = useState<CategoryDetail | null>(null);
  const [moveVisible, setMoveVisible] = useState(false);
  const [moveCategoryId, setMoveCategoryId] = useState<number | null>(null);

  /** 加载分类详情 */
  const loadDetail = useCallback(async (id: number) => {
    try {
      const data = await getById(id);
      setDetail(data);
    } catch {
      setDetail(null);
    }
  }, []);

  /** 加载分类树（同时刷新当前选中节点的详情，确保移动后 sortOrder 同步） */
  const refreshTree = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getTree();
      setTreeData(data);
      // 如果有选中节点，重新加载详情以同步 sortOrder 等字段
      if (selectedId) {
        loadDetail(selectedId);
      }
    } catch {
      // 错误已由全局拦截器处理
    } finally {
      setLoading(false);
    }
  }, [selectedId, loadDetail]);

  // 初始化加载树
  useEffect(() => {
    refreshTree();
  }, [refreshTree]);

  // 选中分类时加载详情
  const handleSelect = useCallback(
    (id: number) => {
      setSelectedId(id);
      loadDetail(id);
    },
    [loadDetail],
  );

  /** 点击新建 */
  const handleCreateClick = useCallback(() => {
    setEditingCategory(null);
    setFormVisible(true);
  }, []);

  /** 点击编辑 */
  const handleEdit = useCallback(() => {
    if (detail) {
      setEditingCategory(detail);
      setFormVisible(true);
    }
  }, [detail]);

  /** 点击移动 */
  const handleMove = useCallback(() => {
    if (detail) {
      setMoveCategoryId(detail.id);
      setMoveVisible(true);
    }
  }, [detail]);

  /** 点击删除 */
  const handleDelete = useCallback(async () => {
    if (!selectedId) return;
    // 前端预检：有 ACTIVE 子分类不允许停用
    const node = findNodeById(treeData, selectedId);
    const hasActiveChildren = (node?.children?.filter((c) => c.status === 'ACTIVE').length ?? 0) > 0;
    if (hasActiveChildren) {
      message.warning('该分类下存在 ACTIVE 子分类，请先停用或移动子分类');
      return;
    }
    try {
      await deleteCategory(selectedId);
      message.success('停用成功');
      setSelectedId(null);
      setDetail(null);
      refreshTree();
    } catch {
      // 错误已由全局拦截器处理
    }
  }, [selectedId, treeData, refreshTree]);

  /** 表单弹窗成功回调 */
  const handleFormSuccess = useCallback(() => {
    setFormVisible(false);
    setEditingCategory(null);
    refreshTree();
    // 如果编辑的是当前选中分类，刷新详情
    if (editingCategory && selectedId === editingCategory.id) {
      loadDetail(editingCategory.id);
    }
    // 新建后选中新分类（树刷新后无法拿到 id，此处不处理选中）
  }, [editingCategory, selectedId, refreshTree, loadDetail]);

  /** 移动弹窗成功回调 */
  const handleMoveSuccess = useCallback(() => {
    setMoveVisible(false);
    setMoveCategoryId(null);
    refreshTree();
    if (selectedId) {
      loadDetail(selectedId);
    }
  }, [selectedId, refreshTree, loadDetail]);

  return (
    <div style={{ padding: 24 }}>
      <Row gutter={16} style={{ height: 'calc(100vh - 112px)' }}>
        {/* 左侧分类树 */}
        <Col span={10}>
          <CategoryTree
            treeData={treeData}
            loading={loading}
            selectedId={selectedId}
            onSelect={handleSelect}
            onRefresh={refreshTree}
            onCreateClick={handleCreateClick}
          />
        </Col>
        {/* 右侧详情面板 */}
        <Col span={14}>
          <CategoryDetailPanel
            detail={detail}
            treeData={treeData}
            onEdit={handleEdit}
            onMove={handleMove}
            onDelete={handleDelete}
          />
        </Col>
      </Row>

      {/* 新建/编辑弹窗 */}
      <CategoryFormModal
        visible={formVisible}
        editingCategory={editingCategory}
        treeData={treeData}
        onSuccess={handleFormSuccess}
        onCancel={() => {
          setFormVisible(false);
          setEditingCategory(null);
        }}
      />

      {/* 移动弹窗 */}
      <MoveModal
        visible={moveVisible}
        categoryId={moveCategoryId}
        treeData={treeData}
        onSuccess={handleMoveSuccess}
        onCancel={() => {
          setMoveVisible(false);
          setMoveCategoryId(null);
        }}
      />
    </div>
  );
};

/** 在树中递归查找指定 id 的节点 */
function findNodeById(
  nodes: CategoryTreeNode[],
  id: number,
): CategoryTreeNode | null {
  for (const node of nodes) {
    if (node.id === id) return node;
    if (node.children) {
      const found = findNodeById(node.children, id);
      if (found) return found;
    }
  }
  return null;
}

export default KnowledgeBase;

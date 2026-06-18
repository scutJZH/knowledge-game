import { useState, useMemo, useCallback } from 'react';
import { Input, Checkbox, Tree, Button, Card, message, Tooltip } from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import type { TreeProps } from 'antd';
import type { CategoryTreeNode } from '@/services/knowledge-category';
import { batchSort, move } from '@/services/knowledge-category';

interface CategoryTreeProps {
  treeData: CategoryTreeNode[];
  loading: boolean;
  selectedId: number | null;
  onSelect: (id: number) => void;
  onRefresh: () => void;
  onCreateClick: () => void;
}

/** 递归过滤树节点，保留匹配节点及其祖先链 */
function filterTree(
  nodes: CategoryTreeNode[],
  keyword: string,
  showInactive: boolean,
): CategoryTreeNode[] {
  if (!keyword && showInactive) return nodes;

  const result: CategoryTreeNode[] = [];

  for (const node of nodes) {
    // 不显示停用且节点为停用状态，检查子节点是否需要保留
    if (!showInactive && node.status === 'INACTIVE') {
      const filteredChildren = filterTree(
        node.children ?? [],
        keyword,
        showInactive,
      );
      if (filteredChildren.length > 0) {
        result.push({ ...node, children: filteredChildren });
      }
      continue;
    }

    const nameMatch = node.name.toLowerCase().includes(keyword.toLowerCase());
    const filteredChildren = filterTree(
      node.children ?? [],
      keyword,
      showInactive,
    );

    // 当前节点匹配或子节点有匹配
    if (nameMatch || filteredChildren.length > 0) {
      result.push({ ...node, children: filteredChildren });
    }
  }

  return result;
}

/** 将 CategoryTreeNode 转换为 Ant Design Tree 的 treeData 格式 */
function toAntTreeData(
  nodes: CategoryTreeNode[],
  showInactive: boolean,
): NonNullable<TreeProps['treeData']> {
  return nodes.map((node) => ({
    key: node.id,
    title: (
      <span
        style={{
          color: !showInactive && node.status === 'INACTIVE' ? '#d9d9d9' : undefined,
        }}
      >
        {node.name}
        {node.status === 'INACTIVE' && showInactive && (
          <span style={{ color: '#999', fontSize: 12, marginLeft: 4 }}>
            (已停用)
          </span>
        )}
      </span>
    ),
    children: node.children
      ? toAntTreeData(node.children, showInactive)
      : undefined,
  }));
}

const CategoryTree: React.FC<CategoryTreeProps> = ({
  treeData,
  loading,
  selectedId,
  onSelect,
  onRefresh,
  onCreateClick,
}) => {
  const [searchValue, setSearchValue] = useState('');
  const [showInactive, setShowInactive] = useState(false);

  // 根据搜索和停用过滤
  const filteredTree = useMemo(
    () => filterTree(treeData, searchValue, showInactive),
    [treeData, searchValue, showInactive],
  );

  // 转换为 Ant Design Tree 格式
  const antTreeData = useMemo(
    () => toAntTreeData(filteredTree, showInactive),
    [filteredTree, showInactive],
  );

  /** 拖拽处理 */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const handleDrop = useCallback(
    async (info: any) => {
      const dragKey = info.dragNode.key as number;
      const dropKey = info.node.key as number;
      const dropToGap = info.dropToGap;

      try {
        // 判断是否跨级拖拽
        const dragSiblings = findSiblings(treeData, dragKey);
        const dropSiblings = findSiblings(treeData, dropKey);
        const isCrossLevel = dropToGap && dragSiblings && dropSiblings
          && dragSiblings.siblings !== dropSiblings.siblings;

        if (dropToGap) {
          if (isCrossLevel) {
            // 跨级 dropToGap：先移动到目标父级
            const targetParentId = dropSiblings!.parent?.id ?? null;
            await move(dragKey, targetParentId);
          } else {
            // 同级排序
            const sortItems = computeSortAfterDrop(
              treeData,
              dragKey,
              dropKey,
              info.dropPosition,
            );
            if (sortItems) {
              await batchSort(sortItems);
            }
          }
        } else {
          // 拖入节点内部（成为子节点）
          await move(dragKey, dropKey);
        }
        onRefresh();
        message.success((dropToGap && !isCrossLevel) ? '排序已更新' : '分类已移动');
      } catch {
        // 错误已由全局拦截器处理
      }
    },
    [treeData, onRefresh],
  );

  return (
    <Card
      title="分类管理"
      extra={
        <Tooltip title="刷新">
          <Button
            type="text"
            icon={<ReloadOutlined />}
            loading={loading}
            onClick={onRefresh}
          />
        </Tooltip>
      }
      style={{ height: '100%' }}
      styles={{ body: { display: 'flex', flexDirection: 'column', height: 'calc(100% - 57px)' } }}
    >
      {/* 搜索框 */}
      <Input
        placeholder="搜索分类名称"
        prefix={<SearchOutlined />}
        allowClear
        value={searchValue}
        onChange={(e) => setSearchValue(e.target.value)}
        style={{ marginBottom: 8 }}
      />

      {/* 显示停用开关 */}
      <Checkbox
        checked={showInactive}
        onChange={(e) => setShowInactive(e.target.checked)}
        style={{ marginBottom: 8 }}
      >
        显示停用分类
      </Checkbox>

      {/* 树组件 */}
      <div style={{ flex: 1, overflow: 'auto' }}>
        <Tree
          treeData={antTreeData}
          selectedKeys={selectedId ? [selectedId] : []}
          onSelect={(keys) => {
            if (keys.length > 0) {
              onSelect(keys[0] as number);
            }
          }}
          draggable
          onDrop={handleDrop}
          defaultExpandAll
          blockNode
        />
      </div>

      {/* 新建按钮 */}
      <Button
        type="primary"
        icon={<PlusOutlined />}
        block
        style={{ marginTop: 8 }}
        onClick={onCreateClick}
      >
        新建分类
      </Button>
    </Card>
  );
};

/**
 * 计算拖拽同级排序后的 batchSort 请求参数
 * dropToGap 场景：dragNode 排到 dropNode 之前或之后（同级）
 */
function computeSortAfterDrop(
  treeData: CategoryTreeNode[],
  dragKey: number,
  dropKey: number,
  dropPosition: number,
): { id: number; sortOrder: number }[] | null {
  // 在整棵树中找到 dropKey 的同级兄弟列表和父节点
  const result = findSiblings(treeData, dropKey);
  if (!result) return null;

  const { siblings } = result;

  // 从兄弟列表中移除 dragKey（如果也在同级中）
  const filtered = siblings.filter((n) => n.id !== dragKey);

  // 找到 dropKey 在 filtered 中的位置
  let insertIndex = filtered.findIndex((n) => n.id === dropKey);

  // dropPosition > 0 表示在 dropNode 之后
  if (dropPosition > 0) {
    insertIndex += 1;
  }

  // 找到 dragNode 本身
  const dragNode = findNodeInList(siblings, dragKey);
  if (!dragNode) {
    // dragNode 不在同级中（跨级拖拽的 dropToGap），需要特殊处理
    // 简化处理：仅重排当前兄弟列表的 sortOrder
  }

  // 构建新顺序：插入 dragNode
  const newOrder = [...filtered];
  if (dragNode) {
    newOrder.splice(insertIndex, 0, dragNode);
  }

  // 生成排序项
  return newOrder.map((node, index) => ({
    id: node.id,
    sortOrder: index,
  }));
}

/** 在树中查找包含指定 key 的同级兄弟列表 */
function findSiblings(
  nodes: CategoryTreeNode[],
  targetKey: number,
): { siblings: CategoryTreeNode[]; parent: CategoryTreeNode | null } | null {
  // 顶级节点
  for (const node of nodes) {
    if (node.id === targetKey) {
      return { siblings: nodes, parent: null };
    }
  }
  // 递归查找
  for (const node of nodes) {
    if (node.children) {
      const result = findSiblings(node.children, targetKey);
      if (result) {
        if (result.parent === null && result.siblings === node.children) {
          return { siblings: node.children, parent: node };
        }
        return result;
      }
    }
  }
  return null;
}

/** 在列表中找节点 */
function findNodeInList(
  nodes: CategoryTreeNode[],
  key: number,
): CategoryTreeNode | null {
  for (const node of nodes) {
    if (node.id === key) return node;
    if (node.children) {
      const found = findNodeInList(node.children, key);
      if (found) return found;
    }
  }
  return null;
}

export default CategoryTree;

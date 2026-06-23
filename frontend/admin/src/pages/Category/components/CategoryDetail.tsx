import dayjs from 'dayjs';
import { Card, Descriptions, Tag, Button, Empty, Image, Popconfirm, Space } from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import type { CategoryDetail, CategoryTreeNode } from '@/services/knowledge-category';

interface CategoryDetailProps {
  detail: CategoryDetail | null;
  treeData: CategoryTreeNode[];
  onEdit: () => void;
  onMove: () => void;
  onToggleStatus: () => void;
  onDelete?: (id: number) => void;
}

/** 在树中递归查找指定 id 节点的 ACTIVE 子节点数量，未找到返回 0 */
function countActiveChildren(nodes: CategoryTreeNode[], id: number): number {
  for (const node of nodes) {
    if (node.id === id) {
      return node.children?.filter((c) => c.status === 'ACTIVE').length ?? 0;
    }
    if (node.children) {
      const count = countActiveChildren(node.children, id);
      if (count > 0) return count;
    }
  }
  return 0;
}

/** 在树中递归查找指定 id 节点的全部子节点数量（含 ACTIVE + INACTIVE） */
function countAllChildren(nodes: CategoryTreeNode[], id: number): number {
  for (const node of nodes) {
    if (node.id === id) {
      return node.children?.length ?? 0;
    }
    if (node.children) {
      const count = countAllChildren(node.children, id);
      if (count > 0) return count;
    }
  }
  return 0;
}

const CategoryDetailPanel: React.FC<CategoryDetailProps> = ({
  detail,
  treeData,
  onEdit,
  onMove,
  onToggleStatus,
  onDelete,
}) => {
  // 未选中分类时显示空状态
  if (!detail) {
    return (
      <Card style={{ height: '100%' }}>
        <Empty description="请在左侧选择一个分类" style={{ marginTop: 100 }} />
      </Card>
    );
  }

  // 预检是否有 ACTIVE 子分类（INACTIVE 子分类不阻止父级停用）
  const hasActiveChildren = countActiveChildren(treeData, detail.id) > 0;
  const allChildrenCount = countAllChildren(treeData, detail.id);

  return (
    <Card
      title={detail.name}
      extra={
        <Space>
          <Button icon={<EditOutlined />} onClick={onEdit}>
            编辑
          </Button>
          <Button icon={<SwapOutlined />} onClick={onMove}>
            移动
          </Button>
          {detail.status === 'ACTIVE' ? (
            <Popconfirm
              title="确认停用"
              description={
                hasActiveChildren
                  ? '该分类下存在 ACTIVE 子分类，请先停用或移动子分类'
                  : `确定要停用分类「${detail.name}」吗？`
              }
              onConfirm={onToggleStatus}
              okText="停用"
              cancelText="取消"
              okButtonProps={hasActiveChildren ? { disabled: true } : { danger: true }}
            >
              <Button danger>
                停用
              </Button>
            </Popconfirm>
          ) : (
            <Popconfirm
              title="确认启用"
              description={`确定要启用分类「${detail.name}」吗？`}
              onConfirm={onToggleStatus}
              okText="启用"
              cancelText="取消"
            >
              <Button style={{ color: '#52c41a', borderColor: '#52c41a' }}>
                启用
              </Button>
            </Popconfirm>
          )}
          {onDelete && (
            <Popconfirm
              title="确认删除"
              description={
                allChildrenCount > 0
                  ? `该分类下有 ${allChildrenCount} 个子分类，将一并移入回收站，确定删除？`
                  : `确定删除分类「${detail.name}」吗？删除后将移入回收站。`
              }
              onConfirm={() => onDelete(detail.id)}
              okText="删除"
              cancelText="取消"
              okButtonProps={{ danger: true }}
            >
              <Button danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      }
      style={{ height: '100%', overflow: 'auto' }}
    >
      <Descriptions column={2} bordered>
        <Descriptions.Item label="ID">{detail.id}</Descriptions.Item>
        <Descriptions.Item label="名称">{detail.name}</Descriptions.Item>
        <Descriptions.Item label="状态">
          <Tag color={detail.status === 'ACTIVE' ? 'green' : 'default'}>
            {detail.status === 'ACTIVE' ? '启用' : '停用'}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="排序号">{detail.sortOrder}</Descriptions.Item>
        <Descriptions.Item label="描述" span={2}>
          {detail.description || '-'}
        </Descriptions.Item>
        <Descriptions.Item label="图标">
          {detail.iconUrl ? (
            <Image src={detail.iconUrl} width={48} height={48} alt="图标" />
          ) : (
            '-'
          )}
        </Descriptions.Item>
        <Descriptions.Item label="封面图">
          {detail.coverImageUrl ? (
            <Image
              src={detail.coverImageUrl}
              width={120}
              alt="封面"
              style={{ borderRadius: 4 }}
            />
          ) : (
            '-'
          )}
        </Descriptions.Item>
        <Descriptions.Item label="颜色">
          {detail.color ? (
            <Space>
              <div
                style={{
                  width: 24,
                  height: 24,
                  backgroundColor: detail.color,
                  borderRadius: 4,
                  border: '1px solid #d9d9d9',
                }}
              />
              <span>{detail.color}</span>
            </Space>
          ) : (
            '-'
          )}
        </Descriptions.Item>
        <Descriptions.Item label="创建时间">
          {detail.createdAt ? dayjs(detail.createdAt).format('YYYY-MM-DD HH:mm:ss') : '-'}
        </Descriptions.Item>
        <Descriptions.Item label="更新时间">
          {detail.updatedAt ? dayjs(detail.updatedAt).format('YYYY-MM-DD HH:mm:ss') : '-'}
        </Descriptions.Item>
      </Descriptions>
    </Card>
  );
};

export default CategoryDetailPanel;

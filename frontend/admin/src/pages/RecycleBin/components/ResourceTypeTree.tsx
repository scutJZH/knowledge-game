import { Tree } from 'antd';
import type { SupportedType } from '@/services/recycleBin';
import { FolderOutlined } from '@ant-design/icons';

const { TreeNode } = Tree;

interface ResourceTypeTreeProps {
  types: SupportedType[];
  onSelect: (key: string) => void;
}

const ResourceTypeTree: React.FC<ResourceTypeTreeProps> = ({ types, onSelect }) => {
  const treeData = (
    <Tree
      defaultSelectedKeys={['ALL']}
      onSelect={(keys) => {
        if (keys.length > 0) onSelect(keys[0] as string);
      }}
      showIcon
    >
      <TreeNode
        key="ALL"
        title="全部"
        icon={<FolderOutlined />}
        isLeaf
      />
      {types.map((t) => (
        <TreeNode
          key={t.type}
          title={t.displayName}
          icon={<FolderOutlined />}
          isLeaf
        />
      ))}
    </Tree>
  );

  return treeData;
};

export default ResourceTypeTree;

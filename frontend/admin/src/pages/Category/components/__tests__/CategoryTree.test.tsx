import React from 'react';
import { render, screen } from '@testing-library/react';

/** 模拟 antd message（避免测试中 DOM 操作报错） */
jest.mock('antd', () => {
  const actual = jest.requireActual('antd');
  return {
    ...actual,
    message: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
  };
});

/** 模拟服务层 */
jest.mock('@/services/knowledge-category', () => ({
  batchSort: jest.fn(),
  move: jest.fn(),
}));

import CategoryTree from '../CategoryTree';

/** 模拟树结构数据 */
const mockTreeData = [
  {
    id: 1,
    parentId: null,
    name: '科学',
    status: 'ACTIVE',
    iconFileId: null,
    iconUrl: null,
    color: null,
    sortOrder: 0,
    children: [
      { id: 2, parentId: 1, name: '物理', status: 'ACTIVE', iconFileId: null, iconUrl: null, color: null, sortOrder: 0 },
    ],
  },
  { id: 3, parentId: null, name: '历史', status: 'INACTIVE', iconFileId: null, iconUrl: null, color: null, sortOrder: 1 },
];

describe('CategoryTree', () => {
  it('渲染分类节点名称', () => {
    render(
      <CategoryTree
        treeData={mockTreeData}
        loading={false}
        selectedId={null}
        onSelect={jest.fn()}
        onRefresh={jest.fn()}
        onCreateClick={jest.fn()}
      />,
    );
    // 默认不显示停用分类，只验证启用状态的节点
    expect(screen.getByText('科学')).toBeInTheDocument();
  });

  it('显示新建分类按钮', () => {
    render(
      <CategoryTree
        treeData={[]}
        loading={false}
        selectedId={null}
        onSelect={jest.fn()}
        onRefresh={jest.fn()}
        onCreateClick={jest.fn()}
      />,
    );
    expect(screen.getByText('新建分类')).toBeInTheDocument();
  });

  it('显示搜索框', () => {
    render(
      <CategoryTree
        treeData={[]}
        loading={false}
        selectedId={null}
        onSelect={jest.fn()}
        onRefresh={jest.fn()}
        onCreateClick={jest.fn()}
      />,
    );
    expect(screen.getByPlaceholderText('搜索分类名称')).toBeInTheDocument();
  });

  it('显示停用分类开关', () => {
    render(
      <CategoryTree
        treeData={[]}
        loading={false}
        selectedId={null}
        onSelect={jest.fn()}
        onRefresh={jest.fn()}
        onCreateClick={jest.fn()}
      />,
    );
    expect(screen.getByText('显示停用分类')).toBeInTheDocument();
  });

  it('显示分类管理标题', () => {
    render(
      <CategoryTree
        treeData={[]}
        loading={false}
        selectedId={null}
        onSelect={jest.fn()}
        onRefresh={jest.fn()}
        onCreateClick={jest.fn()}
      />,
    );
    expect(screen.getByText('分类管理')).toBeInTheDocument();
  });

  it('渲染子节点名称', () => {
    render(
      <CategoryTree
        treeData={mockTreeData}
        loading={false}
        selectedId={null}
        onSelect={jest.fn()}
        onRefresh={jest.fn()}
        onCreateClick={jest.fn()}
      />,
    );
    expect(screen.getByText('物理')).toBeInTheDocument();
  });
});

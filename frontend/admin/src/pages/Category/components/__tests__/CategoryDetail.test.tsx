import React from 'react';
import { render, screen } from '@testing-library/react';
import CategoryDetailPanel from '../CategoryDetail';

/** 模拟分类详情数据 */
const mockDetail = {
  id: 1,
  parentId: null,
  name: '科学',
  description: '科学分类',
  iconFileId: null,
  iconUrl: null,
  color: '#FF5500',
  coverImageFileId: null,
  coverImageUrl: null,
  sortOrder: 0,
  status: 'ACTIVE',
  createdAt: 1767225600000,
  updatedAt: 1767225600000,
};

/** 模拟树结构数据（含子分类） */
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
];

describe('CategoryDetail', () => {
  it('未选中时显示空状态提示', () => {
    render(
      <CategoryDetailPanel
        detail={null}
        treeData={[]}
        onEdit={jest.fn()}
        onMove={jest.fn()}
        onToggleStatus={jest.fn()}
      />,
    );
    expect(screen.getByText('请在左侧选择一个分类')).toBeInTheDocument();
  });

  it('选中时展示分类名称', () => {
    render(
      <CategoryDetailPanel
        detail={mockDetail}
        treeData={[mockTreeData[0]]}
        onEdit={jest.fn()}
        onMove={jest.fn()}
        onToggleStatus={jest.fn()}
      />,
    );
    // Card 标题和描述项中都应出现"科学"
    const elements = screen.getAllByText('科学');
    expect(elements.length).toBeGreaterThanOrEqual(2);
  });

  it('选中时展示启用状态标签', () => {
    render(
      <CategoryDetailPanel
        detail={mockDetail}
        treeData={[]}
        onEdit={jest.fn()}
        onMove={jest.fn()}
        onToggleStatus={jest.fn()}
      />,
    );
    expect(screen.getByText('启用')).toBeInTheDocument();
  });

  it('停用状态展示停用标签', () => {
    const inactiveDetail = { ...mockDetail, status: 'INACTIVE' };
    render(
      <CategoryDetailPanel
        detail={inactiveDetail}
        treeData={[]}
        onEdit={jest.fn()}
        onMove={jest.fn()}
        onToggleStatus={jest.fn()}
      />,
    );
    expect(screen.getByText('停用')).toBeInTheDocument();
  });

  it('展示排序号', () => {
    render(
      <CategoryDetailPanel
        detail={mockDetail}
        treeData={[]}
        onEdit={jest.fn()}
        onMove={jest.fn()}
        onToggleStatus={jest.fn()}
      />,
    );
    expect(screen.getByText('0')).toBeInTheDocument();
  });
});

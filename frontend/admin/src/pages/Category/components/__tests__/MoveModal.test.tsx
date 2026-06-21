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
  move: jest.fn(),
}));

import MoveModal from '../MoveModal';

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
];

describe('MoveModal', () => {
  it('打开时显示"移动分类"标题', () => {
    render(
      <MoveModal
        visible={true}
        categoryId={1}
        treeData={mockTreeData}
        onSuccess={jest.fn()}
        onCancel={jest.fn()}
      />,
    );
    expect(screen.getByText('移动分类')).toBeInTheDocument();
  });

  it('未打开时不渲染', () => {
    render(
      <MoveModal
        visible={false}
        categoryId={1}
        treeData={mockTreeData}
        onSuccess={jest.fn()}
        onCancel={jest.fn()}
      />,
    );
    expect(screen.queryByText('移动分类')).not.toBeInTheDocument();
  });

  it('显示确认移动按钮', () => {
    render(
      <MoveModal
        visible={true}
        categoryId={1}
        treeData={mockTreeData}
        onSuccess={jest.fn()}
        onCancel={jest.fn()}
      />,
    );
    expect(screen.getByText('确认移动')).toBeInTheDocument();
  });

  it('渲染 TreeSelect 选择器', () => {
    render(
      <MoveModal
        visible={true}
        categoryId={1}
        treeData={mockTreeData}
        onSuccess={jest.fn()}
        onCancel={jest.fn()}
      />,
    );
    // Modal 中应包含 TreeSelect 的 placeholder
    expect(screen.getByText('选择目标父级分类，不选则移动到顶级。')).toBeInTheDocument();
  });

  it('显示提示文字', () => {
    render(
      <MoveModal
        visible={true}
        categoryId={1}
        treeData={mockTreeData}
        onSuccess={jest.fn()}
        onCancel={jest.fn()}
      />,
    );
    expect(screen.getByText('选择目标父级分类，不选则移动到顶级。')).toBeInTheDocument();
  });
});

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
  create: jest.fn(),
  update: jest.fn(),
}));

/** 模拟 ImageUploadField 为可控展示组件 */
jest.mock('@/components/ImageUploadField', () => ({
  __esModule: true,
  default: ({ bizType, placeholder }: { bizType: string; placeholder?: string }) => (
    <div data-testid={`image-upload-${bizType}`}>{placeholder}</div>
  ),
}));

import CategoryFormModal from '../CategoryFormModal';

/** 模拟分类树数据 */
const mockTreeData = [
  { id: 1, parentId: null, name: '科学', status: 'ACTIVE', iconUrl: null, color: null, sortOrder: 0 },
];

/** 模拟编辑中的分类数据 */
const mockEditingCategory = {
  id: 1,
  parentId: null,
  name: '科学',
  description: '科学分类',
  iconUrl: null,
  color: null,
  coverImageUrl: null,
  sortOrder: 0,
  status: 'ACTIVE',
  createdAt: 1767225600000,
  updatedAt: 1767225600000,
};

describe('CategoryFormModal', () => {
  it('新建模式下标题为"新建分类"', () => {
    render(
      <CategoryFormModal
        visible={true}
        editingCategory={null}
        treeData={mockTreeData}
        onSuccess={jest.fn()}
        onCancel={jest.fn()}
      />,
    );
    expect(screen.getByText('新建分类')).toBeInTheDocument();
  });

  it('编辑模式下标题为"编辑分类"', () => {
    render(
      <CategoryFormModal
        visible={true}
        editingCategory={mockEditingCategory}
        treeData={mockTreeData}
        onSuccess={jest.fn()}
        onCancel={jest.fn()}
      />,
    );
    expect(screen.getByText('编辑分类')).toBeInTheDocument();
  });

  it('未打开时不渲染弹窗标题', () => {
    render(
      <CategoryFormModal
        visible={false}
        editingCategory={null}
        treeData={mockTreeData}
        onSuccess={jest.fn()}
        onCancel={jest.fn()}
      />,
    );
    expect(screen.queryByText('新建分类')).not.toBeInTheDocument();
  });

  it('渲染分类名称表单项', () => {
    render(
      <CategoryFormModal
        visible={true}
        editingCategory={null}
        treeData={mockTreeData}
        onSuccess={jest.fn()}
        onCancel={jest.fn()}
      />,
    );
    expect(screen.getByText('分类名称')).toBeInTheDocument();
  });

  it('渲染父级分类表单项', () => {
    render(
      <CategoryFormModal
        visible={true}
        editingCategory={null}
        treeData={mockTreeData}
        onSuccess={jest.fn()}
        onCancel={jest.fn()}
      />,
    );
    expect(screen.getByText('父级分类')).toBeInTheDocument();
  });

  it('图标字段应使用 ImageUploadField 组件并传入 bizType=CATEGORY_ICON', () => {
    render(
      <CategoryFormModal
        visible={true}
        editingCategory={null}
        treeData={mockTreeData}
        onSuccess={jest.fn()}
        onCancel={jest.fn()}
      />,
    );
    const el = screen.getByTestId('image-upload-CATEGORY_ICON');
    expect(el).toBeInTheDocument();
  });

  it('封面图字段应使用 ImageUploadField 组件并传入 bizType=CATEGORY_COVER', () => {
    render(
      <CategoryFormModal
        visible={true}
        editingCategory={null}
        treeData={mockTreeData}
        onSuccess={jest.fn()}
        onCancel={jest.fn()}
      />,
    );
    const el = screen.getByTestId('image-upload-CATEGORY_COVER');
    expect(el).toBeInTheDocument();
  });
});

import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';

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
import { update as updateMock, create as createMock } from '@/services/knowledge-category';

const mockUpdate = updateMock as jest.Mock;
const mockCreate = createMock as jest.Mock;

beforeEach(() => {
  mockUpdate.mockReset();
  mockCreate.mockReset();
  mockUpdate.mockResolvedValue(mockEditingCategory);
});

/** 模拟分类树数据 */
const mockTreeData = [
  { id: 1, parentId: null, name: '科学', status: 'ACTIVE', iconFileId: null, iconUrl: null, color: null, sortOrder: 0 },
];

/** 模拟编辑中的分类数据 */
const mockEditingCategory = {
  id: 1,
  parentId: null,
  name: '科学',
  description: '科学分类',
  iconFileId: null,
  iconUrl: null,
  color: null,
  coverImageFileId: null,
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

  /**
   * 三态场景：编辑模式下字段值未变更时，update 只发送最小 payload（不发送未变更字段）
   */
  it('编辑提交时未变更字段不出现在 update payload', async () => {
    render(
      <CategoryFormModal
        visible={true}
        editingCategory={mockEditingCategory}
        treeData={mockTreeData}
        onSuccess={jest.fn()}
        onCancel={jest.fn()}
      />,
    );
    // 不修改任何字段，直接点击"确认"（ModalForm 的提交按钮）
    const submitBtn = screen.getByRole('button', { name: /确\s*认/ });
    fireEvent.click(submitBtn);
    await waitFor(() => expect(mockUpdate).toHaveBeenCalled());
    // 未变更字段（name/description/parentId 等）不应出现在 payload
    expect(mockUpdate).toHaveBeenCalledWith(1, {});
  });

  /**
   * 三态场景：清空 description（用户将描述设为空字符串）→ payload.description = null
   */
  it('清空 description 时 payload.description = null', async () => {
    render(
      <CategoryFormModal
        visible={true}
        editingCategory={{ ...mockEditingCategory, description: '原描述' }}
        treeData={mockTreeData}
        onSuccess={jest.fn()}
        onCancel={jest.fn()}
      />,
    );
    // 清空描述输入框
    const descInput = screen.getByDisplayValue('原描述');
    fireEvent.change(descInput, { target: { value: '' } });
    const submitBtn = screen.getByRole('button', { name: /确\s*认/ });
    fireEvent.click(submitBtn);
    await waitFor(() => expect(mockUpdate).toHaveBeenCalled());
    expect(mockUpdate).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ description: null }),
    );
  });

  /**
   * 三态场景：修改 name（必填字段）→ payload.name = 新值
   */
  it('修改 name 时 payload.name 发送新值', async () => {
    render(
      <CategoryFormModal
        visible={true}
        editingCategory={mockEditingCategory}
        treeData={mockTreeData}
        onSuccess={jest.fn()}
        onCancel={jest.fn()}
      />,
    );
    const nameInput = screen.getByDisplayValue('科学');
    fireEvent.change(nameInput, { target: { value: '新科学' } });
    const submitBtn = screen.getByRole('button', { name: /确\s*认/ });
    fireEvent.click(submitBtn);
    await waitFor(() => expect(mockUpdate).toHaveBeenCalled());
    expect(mockUpdate).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ name: '新科学' }),
    );
  });
});

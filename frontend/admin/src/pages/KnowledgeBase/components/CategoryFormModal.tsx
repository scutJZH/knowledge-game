import { useEffect } from 'react';
import { ModalForm, ProFormText, ProFormTextArea, ProFormDigit, ProFormTreeSelect } from '@ant-design/pro-components';
import { message } from 'antd';
import type { CategoryDetail, CategoryTreeNode, CategoryFormData } from '@/services/knowledge-category';
import { create, update } from '@/services/knowledge-category';

interface CategoryFormModalProps {
  visible: boolean;
  editingCategory: CategoryDetail | null;
  treeData: CategoryTreeNode[];
  onSuccess: () => void;
  onCancel: () => void;
}

/** 将分类树转为 TreeSelect 的 treeData 格式 */
function toTreeSelectData(
  nodes: CategoryTreeNode[],
  excludeId?: number,
): { title: string; value: number; children?: any[] }[] {
  return nodes
    .filter((n) => n.id !== excludeId)
    .map((node) => ({
      title: node.name,
      value: node.id,
      children: node.children
        ? toTreeSelectData(node.children, excludeId)
        : undefined,
    }));
}

const CategoryFormModal: React.FC<CategoryFormModalProps> = ({
  visible,
  editingCategory,
  treeData,
  onSuccess,
  onCancel,
}) => {
  const isEdit = editingCategory !== null;

  // TreeSelect 数据（编辑时排除自身，避免自引用）
  const treeSelectData = toTreeSelectData(
    treeData,
    isEdit ? editingCategory.id : undefined,
  );

  return (
    <ModalForm
      title={isEdit ? '编辑分类' : '新建分类'}
      open={visible}
      onFinish={async (values) => {
        try {
          const formData: CategoryFormData = {
            parentId: values.parentId ?? null,
            name: values.name,
            description: values.description || undefined,
            iconUrl: values.iconUrl || undefined,
            color: values.color || undefined,
            coverImageUrl: values.coverImageUrl || undefined,
            sortOrder: values.sortOrder ?? undefined,
          };

          if (isEdit) {
            await update(editingCategory.id, formData);
            message.success('更新成功');
          } else {
            await create(formData);
            message.success('创建成功');
          }
          onSuccess();
          return true;
        } catch {
          return false;
        }
      }}
      onOpenChange={(open) => {
        if (!open) onCancel();
      }}
      initialValues={
        isEdit
          ? {
              parentId: editingCategory.parentId,
              name: editingCategory.name,
              description: editingCategory.description ?? '',
              iconUrl: editingCategory.iconUrl ?? '',
              color: editingCategory.color ?? '',
              coverImageUrl: editingCategory.coverImageUrl ?? '',
              sortOrder: editingCategory.sortOrder,
            }
          : {
              parentId: null,
              sortOrder: undefined,
            }
      }
      modalProps={{ destroyOnClose: true }}
    >
      <ProFormTreeSelect
        name="parentId"
        label="父级分类"
        placeholder="不选则为顶级分类"
        fieldProps={{
          treeData: treeSelectData,
          allowClear: true,
          showSearch: true,
          treeNodeFilterProp: 'title',
          disabled: isEdit,
          treeDefaultExpandAll: true,
        }}
        tooltip="编辑模式下不可修改父级分类，请使用移动功能"
      />

      <ProFormText
        name="name"
        label="分类名称"
        placeholder="请输入分类名称"
        rules={[{ required: true, message: '请输入分类名称' }]}
      />

      <ProFormTextArea
        name="description"
        label="描述"
        placeholder="请输入分类描述"
        fieldProps={{ rows: 3 }}
      />

      <ProFormText
        name="iconUrl"
        label="图标 URL"
        placeholder="请输入图标图片地址"
      />

      <ProFormText
        name="coverImageUrl"
        label="封面图 URL"
        placeholder="请输入封面图片地址"
      />

      <ProFormText
        name="color"
        label="颜色"
        placeholder="请输入颜色值，如 #FF5500"
        rules={[
          {
            pattern: /^$|^#[0-9A-Fa-f]{6}$/,
            message: '请输入合法的十六进制颜色值，如 #FF5500',
          },
        ]}
      />

      <ProFormDigit
        name="sortOrder"
        label="排序号"
        placeholder="不填则自动分配"
        min={0}
        fieldProps={{ precision: 0 }}
      />
    </ModalForm>
  );
};

export default CategoryFormModal;

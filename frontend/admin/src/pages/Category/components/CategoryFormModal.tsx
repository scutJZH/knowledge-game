import { ModalForm, ProForm, ProFormText, ProFormTextArea, ProFormDigit, ProFormSelect, ProFormTreeSelect } from '@ant-design/pro-components';
import { message } from 'antd';
import type { CategoryDetail, CategoryTreeNode, CategoryFormData, CategoryUpdateData } from '@/services/knowledge-category';
import { create, update } from '@/services/knowledge-category';
import ImageUploadField from '@/components/ImageUploadField';

/**
 * 构造更新负载：按字段对比，未变更的字段不发送（undefined 三态）。
 * 必填字段（name/sortOrder/parentId）保持原值，可清空字段（description/iconFileId/color/coverImageFileId）
 * 与原值不同则发送新值（含 null=清空）。
 */
function buildUpdatePayload(
  original: CategoryDetail,
  values: CategoryFormData,
): CategoryUpdateData {
  const payload: CategoryUpdateData = {};

  if (values.name !== original.name) {
    payload.name = values.name;
  }
  if ((values.sortOrder ?? null) !== (original.sortOrder ?? null)) {
    payload.sortOrder = values.sortOrder;
  }
  if ((values.parentId ?? null) !== (original.parentId ?? null)) {
    payload.parentId = values.parentId ?? null;
  }

  const nextDesc = values.description || null;
  if (nextDesc !== original.description) {
    payload.description = nextDesc;
  }
  const nextIcon = values.iconFileId ?? null;
  if (nextIcon !== original.iconFileId) {
    payload.iconFileId = nextIcon;
  }
  const nextCover = values.coverImageFileId ?? null;
  if (nextCover !== original.coverImageFileId) {
    payload.coverImageFileId = nextCover;
  }
  const nextColor = values.color || null;
  if (nextColor !== original.color) {
    payload.color = nextColor;
  }
  const nextStatus = values.status ?? undefined;
  if (nextStatus && nextStatus !== original.status) {
    payload.status = nextStatus;
  }

  return payload;
}

interface CategoryFormModalProps {
  visible: boolean;
  editingCategory: CategoryDetail | null;
  treeData: CategoryTreeNode[];
  onSuccess: () => void;
  onCancel: () => void;
}

/** 将分类树转为 TreeSelect 的 treeData 格式（过滤 INACTIVE + 排除自身） */
function toTreeSelectData(
  nodes: CategoryTreeNode[],
  excludeId?: number,
): { title: string; value: number; children?: any[] }[] {
  return nodes
    .filter((n) => n.id !== excludeId && n.status === 'ACTIVE')
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
          if (isEdit) {
            const payload = buildUpdatePayload(editingCategory, values as CategoryFormData);
            await update(editingCategory.id, payload);
            message.success('更新成功');
          } else {
            const formData: CategoryFormData = {
              parentId: values.parentId ?? null,
              name: values.name,
              description: values.description || undefined,
              iconFileId: values.iconFileId ?? undefined,
              color: values.color || undefined,
              coverImageFileId: values.coverImageFileId ?? undefined,
              sortOrder: values.sortOrder ?? undefined,
            };
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
              iconFileId: editingCategory.iconFileId ?? undefined,
              color: editingCategory.color ?? '',
              coverImageFileId: editingCategory.coverImageFileId ?? undefined,
              sortOrder: editingCategory.sortOrder,
              status: editingCategory.status,
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

      <ProForm.Item name="iconFileId" label="图标">
        <ImageUploadField
          bizType="CATEGORY_ICON"
          placeholder="上传图标"
          url={editingCategory?.iconUrl ?? undefined}
        />
      </ProForm.Item>

      <ProForm.Item name="coverImageFileId" label="封面图">
        <ImageUploadField
          bizType="CATEGORY_COVER"
          placeholder="上传封面图"
          url={editingCategory?.coverImageUrl ?? undefined}
        />
      </ProForm.Item>

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

      {isEdit && (
        <ProFormSelect
          name="status"
          label="状态"
          valueEnum={{
            ACTIVE: '启用',
            INACTIVE: '停用',
          }}
          rules={[{ required: true, message: '请选择状态' }]}
        />
      )}
    </ModalForm>
  );
};

export default CategoryFormModal;

import {
  DrawerForm,
  ProFormDigit,
  ProFormSelect,
  ProFormText,
  ProFormTreeSelect,
} from '@ant-design/pro-components';
import { Form, message, TreeSelect } from 'antd';
import { useEffect, useState } from 'react';
import ImageUploadField from '@/components/ImageUploadField';
import VditorEditor from '@/components/VditorEditor';
import {
  createKnowledgeItem,
  getKnowledgeItemCategories,
  updateKnowledgeItem,
  updateKnowledgeItemCategories,
} from '@/services/knowledge-item';
import {
  getTree,
  convertToTreeDataActiveOnly,
  type CategoryTreeNode,
} from '@/services/knowledge-category';

interface KnowledgeItemFormDrawerProps {
  open: boolean;
  mode: 'create' | 'edit';
  initialValues?: Record<string, any>;
  onClose: () => void;
  onSubmit: () => void;
}

const KnowledgeItemFormDrawer: React.FC<KnowledgeItemFormDrawerProps> = ({
  open,
  mode,
  initialValues,
  onClose,
  onSubmit,
}) => {
  const [form] = Form.useForm();
  const [categoryTree, setCategoryTree] = useState<CategoryTreeNode[]>([]);
  const [editId, setEditId] = useState<number | null>(null);

  useEffect(() => {
    getTree()
      .then((data) => setCategoryTree(data || []))
      .catch(() => {}); // 错误已由全局拦截器展示
  }, []);

  useEffect(() => {
    if (open) {
      if (mode === 'edit' && initialValues?.id) {
        setEditId(initialValues.id);
        form.setFieldsValue(initialValues);
        getKnowledgeItemCategories(initialValues.id).then((res) => {
          form.setFieldValue('categoryIds', res || []);
        });
      } else {
        setEditId(null);
        form.resetFields();
      }
    }
  }, [open, mode, initialValues, form]);

  const handleFinish = async (values: Record<string, any>) => {
    try {
      if (mode === 'create') {
        await createKnowledgeItem({
          title: values.title,
          content: values.content,
          coverImageFileId: values.coverImageFileId || null,
          tags: values.tags || [],
          sortOrder: values.sortOrder ?? 0,
          categoryIds: values.categoryIds || [],
        });
        message.success('创建成功');
      } else if (editId) {
        await updateKnowledgeItem(editId, {
          title: values.title,
          content: values.content,
          coverImageFileId: values.coverImageFileId || null,
          tags: values.tags || [],
          sortOrder: values.sortOrder,
        });
        if (values.categoryIds) {
          await updateKnowledgeItemCategories(editId, {
            categoryIds: values.categoryIds,
          });
        }
        message.success('更新成功');
      }
      onSubmit();
      return true;
    } catch {
      return false;
    }
  };

  return (
    <DrawerForm
      title={mode === 'create' ? '新建知识条目' : '编辑知识条目'}
      open={open}
      form={form}
      onOpenChange={(visible) => {
        if (!visible) onClose();
      }}
      onFinish={handleFinish}
      drawerProps={{ width: 800 }}
      initialValues={initialValues}
    >
      <ProFormText
        name="title"
        label="标题"
        rules={[
          { required: true, message: '标题不能为空' },
          { max: 200, message: '标题不超过 200 字' },
        ]}
      />
      <Form.Item
        name="coverImageFileId"
        label="封面图"
        getValueFromEvent={(val: number) => val}
      >
        <ImageUploadField
          bizType="KNOWLEDGE_ITEM_COVER"
          url={initialValues?.coverImageUrl}
        />
      </Form.Item>
      <Form.Item
        name="content"
        label="内容"
        rules={[{ required: true, message: '内容不能为空' }]}
      >
        <VditorEditor />
      </Form.Item>
      <ProFormTreeSelect
        name="categoryIds"
        label="分类"
        rules={[{ required: true, message: '请选择分类' }]}
        fieldProps={{
          treeData: convertToTreeDataActiveOnly(categoryTree),
          multiple: true,
          treeCheckable: true,
          showCheckedStrategy: TreeSelect.SHOW_ALL,
          placeholder: '请选择分类',
        }}
      />
      <ProFormSelect
        name="tags"
        label="标签"
        fieldProps={{
          mode: 'tags',
          tokenSeparators: [','],
          placeholder: '输入标签后按回车添加',
        }}
      />
      <ProFormDigit name="sortOrder" label="排序号" min={0} initialValue={0} />
    </DrawerForm>
  );
};

export default KnowledgeItemFormDrawer;

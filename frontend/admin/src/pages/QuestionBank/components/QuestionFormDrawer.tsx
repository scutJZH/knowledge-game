import React, { useEffect, useRef } from 'react';
import {
  Button,
  Checkbox,
  Drawer,
  Form,
  Input,
  message,
  Radio,
  Select,
  Space,
  Tag,
  TreeSelect,
} from 'antd';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import type { CategoryTreeNode } from '@/services/knowledge-category';
import { convertToTreeDataActiveOnly } from '@/services/knowledge-category';
import type { QuestionResponse, CreateQuestionRequest, UpdateQuestionRequest } from '@/services/questionBank';
import {
  DIFFICULTY_OPTIONS,
  QUESTION_TYPE_OPTIONS,
  createQuestion,
  updateQuestion,
  updateQuestionCategories,
} from '@/services/questionBank';

/** 选项键列表 */
const OPTION_KEYS = ['A', 'B', 'C', 'D', 'E', 'F'];

/** 答案值联合类型：4 种题型的答案在表单中的类型 */
export type AnswerValue = string | string[] | boolean | undefined;

/** 表单值类型 */
interface QuestionFormValues {
  type: string;
  difficulty: number;
  content: string;
  options: { key: string; content: string }[];
  answer: AnswerValue;
  explanation: string;
  tags: string[];
  categoryIds: number[];
}

/** 抽屉组件 Props */
export interface QuestionFormDrawerProps {
  open: boolean;
  mode: 'create' | 'edit';
  initialValues?: QuestionResponse;
  categoryTree: CategoryTreeNode[];
  onSubmit: () => void;
  onClose: () => void;
}

/** 将后端 answer 字符串解析为表单值（导出供测试使用） */
export function parseAnswer(type: string, answerRaw: string): AnswerValue {
  if (type === 'SINGLE_CHOICE') {
    return answerRaw;
  }
  if (type === 'MULTIPLE_CHOICE' || type === 'FILL_BLANK') {
    try {
      return JSON.parse(answerRaw);
    } catch {
      // 容错：解析失败时原字符串作为单元素数组展示
      return [answerRaw];
    }
  }
  if (type === 'TRUE_FALSE') {
    return answerRaw === 'true';
  }
  return answerRaw;
}

/** 将表单 answer 值序列化为后端需要的 String */
function serializeAnswer(type: string, answer: AnswerValue): string {
  if (type === 'SINGLE_CHOICE') {
    return answer as string;
  }
  if (type === 'MULTIPLE_CHOICE' || type === 'FILL_BLANK') {
    return JSON.stringify(answer);
  }
  if (type === 'TRUE_FALSE') {
    return String(answer);
  }
  return String(answer);
}

/** 将 QuestionResponse 转为表单初始值 */
function toFormValues(detail: QuestionResponse): QuestionFormValues {
  return {
    type: detail.type,
    difficulty: detail.difficulty,
    content: detail.content,
    options: detail.options || [],
    answer: parseAnswer(detail.type, detail.answer),
    explanation: detail.explanation || '',
    tags: detail.tags || [],
    categoryIds: detail.categoryIds || [],
  };
}

/** 规范化选项 key（按位置重分配 A/B/C/D/E/F）并将旧答案 key 映射为新 key（导出供测试使用） */
export function normalizeOptionsAndAnswer(
  options: { key: string; content: string }[] | undefined,
  answer: AnswerValue,
  type: string,
): { options: { key: string; content: string }[]; answer: AnswerValue } {
  if (!options || options.length === 0) {
    return { options: options || [], answer };
  }

  // 构建旧 key → 新 key 映射
  const keyMap = new Map<string, string>();
  const normalizedOptions = options.map((opt, idx) => {
    const newKey = OPTION_KEYS[idx];
    keyMap.set(opt.key, newKey);
    return { ...opt, key: newKey };
  });

  // 映射答案值中的旧 key → 新 key
  let normalizedAnswer: AnswerValue = answer;
  if (type === 'SINGLE_CHOICE' && typeof answer === 'string') {
    normalizedAnswer = keyMap.get(answer) || answer;
  } else if (type === 'MULTIPLE_CHOICE' && Array.isArray(answer)) {
    normalizedAnswer = (answer as string[]).map((k) => keyMap.get(k) || k);
  }

  return { options: normalizedOptions, answer: normalizedAnswer };
}

/** 创建模式的表单默认值 */
const CREATE_DEFAULTS: QuestionFormValues = {
  type: 'SINGLE_CHOICE',
  difficulty: 1,
  content: '',
  options: [
    { key: 'A', content: '' },
    { key: 'B', content: '' },
  ],
  answer: undefined,
  explanation: '',
  tags: [],
  categoryIds: [],
};

/** 题库表单抽屉 */
const QuestionFormDrawer: React.FC<QuestionFormDrawerProps> = ({
  open,
  mode,
  initialValues,
  categoryTree,
  onSubmit,
  onClose,
}) => {
  const [form] = Form.useForm<QuestionFormValues>();
  const [submitting, setSubmitting] = React.useState(false);
  const prevTypeRef = useRef<string | undefined>(undefined);
  /** 区分 × 按钮删除（true）和 toggle 删除（false） */
  const tagCloseRef = useRef(false);
  /** 缓存上一次标签值，用于检测 toggle 行为 */
  const prevTagsRef = useRef<string[]>([]);

  const isEdit = mode === 'edit';
  const drawerTitle = isEdit ? '编辑题目' : '新建题目';

  /** 重置表单：打开/切换模式时 */
  useEffect(() => {
    if (!open) return;
    if (isEdit && initialValues) {
      const vals = toFormValues(initialValues);
      form.setFieldsValue(vals);
      prevTypeRef.current = vals.type;
    } else {
      form.setFieldsValue(CREATE_DEFAULTS);
      prevTypeRef.current = CREATE_DEFAULTS.type;
    }
  }, [open, mode, initialValues, form, isEdit]);

  /** 监听题型变化，处理切换 UX（仅创建模式） */
  const handleTypeChange = (newType: string) => {
    if (isEdit) return;
    const prevType = prevTypeRef.current;
    prevTypeRef.current = newType;

    const currentOptions: { key: string; content: string }[] = form.getFieldValue('options') || [];

    // 切换到选择题
    if (newType === 'SINGLE_CHOICE' || newType === 'MULTIPLE_CHOICE') {
      if (prevType === 'SINGLE_CHOICE' || prevType === 'MULTIPLE_CHOICE') {
        // 单选 ↔ 多选：保留选项，转换答案格式
        const answer = form.getFieldValue('answer');
        if (newType === 'SINGLE_CHOICE' && Array.isArray(answer)) {
          form.setFieldValue('answer', answer.length > 0 ? answer[0] : undefined);
        } else if (newType === 'MULTIPLE_CHOICE' && !Array.isArray(answer) && answer !== undefined) {
          form.setFieldValue('answer', [answer]);
        }
      } else {
        // 从非选择题切来：保留现有选项或补默认值，清空答案
        let options = currentOptions.length >= 2
          ? currentOptions.map((opt, idx) => ({ ...opt, key: OPTION_KEYS[idx] }))
          : [
              { key: 'A', content: currentOptions[0]?.content || '' },
              { key: 'B', content: currentOptions[1]?.content || '' },
            ];
        form.setFieldsValue({ options, answer: undefined });
      }
    }

    // 切换到判断：清空选项，答案默认 true
    if (newType === 'TRUE_FALSE') {
      form.setFieldsValue({ options: [], answer: true });
    }

    // 切换到填空：清空选项，答案默认空列表
    if (newType === 'FILL_BLANK') {
      form.setFieldsValue({ options: [], answer: [] });
    }
  };

  /** 手动校验 Form.List 字段（antd 5 Form.List 不支持 rules 属性，用 message.error 展示） */
  const validateListFields = (values: QuestionFormValues): boolean => {
    // 校验选项（仅选择题）
    if (values.type === 'SINGLE_CHOICE' || values.type === 'MULTIPLE_CHOICE') {
      const filledOptions = (values.options || []).filter(
        (o) => o.content && o.content.trim(),
      );
      if (filledOptions.length < 2) {
        message.error('至少需要 2 个非空选项');
        return false;
      }
      // 校验选项内容不重复
      const contents = filledOptions.map((o) => o.content.trim());
      if (new Set(contents).size < contents.length) {
        message.error('选项内容不能重复');
        return false;
      }
    }

    // 校验多选答案
    if (values.type === 'MULTIPLE_CHOICE') {
      if (!values.answer || !Array.isArray(values.answer) || values.answer.length < 2) {
        message.error('多选题至少选择 2 个答案');
        return false;
      }
    }

    // 校验填空答案
    if (values.type === 'FILL_BLANK') {
      const answerArray = Array.isArray(values.answer) ? values.answer : [];
      const filled = answerArray.filter(
        (item) => typeof item === 'string' && item.trim(),
      );
      if (filled.length < 1) {
        message.error('至少需要 1 个非空关键词');
        return false;
      }
    }

    return true;
  };

  /** 提交表单 */
  const handleFinish = async (values: QuestionFormValues) => {
    // 手动校验 Form.List 字段
    if (!validateListFields(values)) {
      return;
    }

    setSubmitting(true);
    try {
      // 规范化选项 key 和答案
      const { options, answer } = normalizeOptionsAndAnswer(
        values.options,
        values.answer,
        values.type,
      );

      if (isEdit && initialValues) {
        // 编辑模式：两次请求
        const body: UpdateQuestionRequest = {
          content: values.content,
          difficulty: values.difficulty as 1 | 2 | 3,
          explanation: values.explanation || undefined,
          tags: values.tags?.length ? values.tags : undefined,
          options: null,
          answer: serializeAnswer(values.type, answer),
        };

        if (values.type === 'SINGLE_CHOICE' || values.type === 'MULTIPLE_CHOICE') {
          body.options = options;
        }

        await updateQuestion(initialValues.id, body);

        // 仅当分类变更时才调 updateQuestionCategories，避免后端重复插入
        const newIds = values.categoryIds || [];
        const oldIds = initialValues.categoryIds || [];
        const idsChanged =
          newIds.length !== oldIds.length ||
          !newIds.every((id) => oldIds.includes(id));
        if (idsChanged) {
          await updateQuestionCategories(initialValues.id, newIds);
        }

        message.success('更新成功');
      } else {
        // 创建模式：一次性提交
        const body: CreateQuestionRequest = {
          type: values.type as CreateQuestionRequest['type'],
          content: values.content,
          difficulty: values.difficulty as 1 | 2 | 3,
          explanation: values.explanation || undefined,
          tags: values.tags?.length ? values.tags : undefined,
          categoryIds: values.categoryIds?.length ? values.categoryIds : undefined,
          options: null,
          answer: serializeAnswer(values.type, answer),
        };

        if (values.type === 'SINGLE_CHOICE' || values.type === 'MULTIPLE_CHOICE') {
          body.options = options;
        }

        await createQuestion(body);
        message.success('创建成功');
      }

      onSubmit();
    } catch {
      // 错误已由 request 拦截器展示，保持 Drawer 打开
    } finally {
      setSubmitting(false);
    }
  };

  /** 表单值监听（用于动态渲染） */
  const currentOptions: { key: string; content: string }[] =
    Form.useWatch('options', form) || [];
  const currentType: string | undefined = Form.useWatch('type', form);

  /** 选择题选项是否显示 */
  const showOptions =
    currentType === 'SINGLE_CHOICE' || currentType === 'MULTIPLE_CHOICE';

  return (
    <Drawer
      title={drawerTitle}
      open={open}
      onClose={onClose}
      width={720}
      destroyOnClose
      extra={
        <Space>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" loading={submitting} onClick={() => form.submit()}>
            提交
          </Button>
        </Space>
      }
    >
      <Form
        form={form}
        layout="vertical"
        onFinish={handleFinish}
        initialValues={isEdit ? undefined : CREATE_DEFAULTS}
      >
        {/* 题型 */}
        <Form.Item
          name="type"
          label="题型"
          rules={[{ required: true, message: '请选择题型' }]}
        >
          <Select
            disabled={isEdit}
            placeholder="请选择题型"
            options={QUESTION_TYPE_OPTIONS.map((o) => ({
              label: o.label,
              value: o.value,
            }))}
            onChange={handleTypeChange}
          />
        </Form.Item>

        {/* 难度 */}
        <Form.Item
          name="difficulty"
          label="难度"
          rules={[{ required: true, message: '请选择难度' }]}
        >
          <Select
            placeholder="请选择难度"
            options={DIFFICULTY_OPTIONS.map((o) => ({
              label: o.label,
              value: o.value,
            }))}
          />
        </Form.Item>

        {/* 题目内容 */}
        <Form.Item
          name="content"
          label="题目内容"
          rules={[
            { required: true, message: '请输入题目内容' },
            { max: 500, message: '题目内容不超过 500 字' },
          ]}
        >
          <Input.TextArea
            placeholder="请输入题目内容"
            maxLength={500}
            rows={3}
            showCount
          />
        </Form.Item>

        {/* 选项区域（仅选择题显示） */}
        {showOptions && (
          <Form.List name="options">
            {(fields, { add, remove }) => (
              <>
                {fields.map((field, index) => (
                  <Form.Item
                    key={field.key}
                    label={`选项 ${OPTION_KEYS[index]}`}
                    required
                  >
                    <Space style={{ display: 'flex' }} align="baseline">
                      <Form.Item
                        {...field}
                        name={[field.name, 'key']}
                        hidden
                        initialValue={OPTION_KEYS[index]}
                      >
                        <Input />
                      </Form.Item>
                      <Form.Item
                        {...field}
                        name={[field.name, 'content']}
                        rules={[
                          { required: true, message: '请输入选项内容' },
                        ]}
                        noStyle
                      >
                        <Input
                          placeholder={`选项 ${OPTION_KEYS[index]} 内容`}
                          style={{ width: 420 }}
                        />
                      </Form.Item>
                      {fields.length > 2 && (
                        <MinusCircleOutlined
                          onClick={() => remove(field.name)}
                          style={{ color: '#ff4d4f', cursor: 'pointer' }}
                        />
                      )}
                    </Space>
                  </Form.Item>
                ))}
                <Form.Item>
                  <Button
                    type="dashed"
                    onClick={() => {
                      if (fields.length >= 6) {
                        message.warning('最多 6 个选项');
                        return;
                      }
                      add({ key: OPTION_KEYS[fields.length], content: '' });
                    }}
                    block
                    icon={<PlusOutlined />}
                  >
                    新增选项（{fields.length}/6）
                  </Button>
                </Form.Item>
              </>
            )}
          </Form.List>
        )}

        {/* 答案区域 — 单选 */}
        {currentType === 'SINGLE_CHOICE' && (
          <Form.Item
            name="answer"
            label="正确答案"
            rules={[{ required: true, message: '请选择正确答案' }]}
          >
            <Radio.Group>
              <Space direction="vertical">
                {currentOptions.map((opt, idx) => (
                  <Radio key={idx} value={OPTION_KEYS[idx]}>
                    {OPTION_KEYS[idx]}
                    {opt.content ? `. ${opt.content}` : ''}
                  </Radio>
                ))}
              </Space>
            </Radio.Group>
          </Form.Item>
        )}

        {/* 答案区域 — 多选 */}
        {currentType === 'MULTIPLE_CHOICE' && (
          <Form.Item
            name="answer"
            label="正确答案"
            rules={[{ required: true, message: '请选择正确答案' }]}
          >
            <Checkbox.Group>
              <Space direction="vertical">
                {currentOptions.map((opt, idx) => (
                  <Checkbox key={idx} value={OPTION_KEYS[idx]}>
                    {OPTION_KEYS[idx]}
                    {opt.content ? `. ${opt.content}` : ''}
                  </Checkbox>
                ))}
              </Space>
            </Checkbox.Group>
          </Form.Item>
        )}

        {/* 答案区域 — 判断 */}
        {currentType === 'TRUE_FALSE' && (
          <Form.Item
            name="answer"
            label="正确答案"
            rules={[{ required: true, message: '请选择正确答案' }]}
          >
            <Radio.Group>
              <Radio value={true}>对</Radio>
              <Radio value={false}>错</Radio>
            </Radio.Group>
          </Form.Item>
        )}

        {/* 答案区域 — 填空 */}
        {currentType === 'FILL_BLANK' && (
          <Form.List name="answer">
            {(fields, { add, remove }) => (
              <>
                {fields.map((field, index) => (
                  <Form.Item
                    key={field.key}
                    label={`关键词 ${index + 1}`}
                    required
                  >
                    <Space style={{ display: 'flex' }} align="baseline">
                      <Form.Item
                        {...field}
                        rules={[{ required: true, message: '请输入关键词' }]}
                        noStyle
                      >
                        <Input placeholder="请输入关键词" style={{ width: 420 }} />
                      </Form.Item>
                      {fields.length > 1 && (
                        <MinusCircleOutlined
                          onClick={() => remove(field.name)}
                          style={{ color: '#ff4d4f', cursor: 'pointer' }}
                        />
                      )}
                    </Space>
                  </Form.Item>
                ))}
                <Form.Item>
                  <Button
                    type="dashed"
                    onClick={() => add('')}
                    block
                    icon={<PlusOutlined />}
                  >
                    新增关键词
                  </Button>
                </Form.Item>
              </>
            )}
          </Form.List>
        )}

        {/* 解析 */}
        <Form.Item
          name="explanation"
          label="解析"
          rules={[{ max: 1000, message: '解析不超过 1000 字' }]}
        >
          <Input.TextArea
            placeholder="请输入解析（选填）"
            maxLength={1000}
            rows={2}
            showCount
          />
        </Form.Item>

        {/* 标签 */}
        <Form.Item name="tags" label="标签">
          <Select
            mode="tags"
            placeholder="请输入标签后回车（选填）"
            maxTagCount={10}
            tokenSeparators={[',']}
            tagRender={(props) => {
              const { label, closable, onClose } = props;
              const handleClose = () => {
                tagCloseRef.current = true;
                onClose();
              };
              return (
                <Tag
                  closable={closable}
                  onClose={handleClose}
                  style={{ marginRight: 3 }}
                >
                  {label}
                </Tag>
              );
            }}
            onChange={(value: string[]) => {
              const cleaned = value.filter((v) => v.trim());
              const deduped = Array.from(new Set(cleaned));
              // 点击 × 删除：允许（tagCloseRef 已在 tagRender.onClose 中置位）
              if (tagCloseRef.current) {
                tagCloseRef.current = false;
                prevTagsRef.current = deduped;
                if (deduped.length !== value.length) {
                  form.setFieldValue('tags', deduped);
                }
                return;
              }
              // 输入已有标签触发 toggle 删除：恢复被删的标签
              const prev = prevTagsRef.current;
              if (value.length < prev.length) {
                form.setFieldValue('tags', prev);
                return;
              }
              // 正常新增：去重后写入
              prevTagsRef.current = deduped;
              if (deduped.length !== value.length) {
                form.setFieldValue('tags', deduped);
              }
            }}
          />
        </Form.Item>

        {/* 知识点分类 */}
        <Form.Item name="categoryIds" label="知识点分类">
          <TreeSelect
            multiple
            placeholder="请选择知识点分类（选填）"
            treeData={convertToTreeDataActiveOnly(categoryTree)}
            allowClear
            treeDefaultExpandAll
          />
        </Form.Item>
      </Form>
    </Drawer>
  );
};

export default QuestionFormDrawer;

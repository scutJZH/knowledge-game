import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import QuestionFormDrawer, { parseAnswer, normalizeOptionsAndAnswer } from '../QuestionFormDrawer';

// mock 服务层
const mockCreateQuestion = jest.fn();
const mockUpdateQuestion = jest.fn();
const mockUpdateQuestionCategories = jest.fn();

jest.mock('@/services/questionBank', () => ({
  ...jest.requireActual('@/services/questionBank'),
  createQuestion: (...args: any[]) => mockCreateQuestion(...args),
  updateQuestion: (...args: any[]) => mockUpdateQuestion(...args),
  updateQuestionCategories: (...args: any[]) => mockUpdateQuestionCategories(...args),
}));

/** 空分类树 */
const emptyTree: any[] = [];

describe('QuestionFormDrawer — 渲染', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('应渲染题型、难度、题目内容等公共字段', () => {
    render(
      <QuestionFormDrawer
        open={true}
        mode="create"
        categoryTree={emptyTree}
        onSubmit={jest.fn()}
        onClose={jest.fn()}
      />,
    );
    expect(screen.getByText('题型')).toBeInTheDocument();
    expect(screen.getByText('难度')).toBeInTheDocument();
    expect(screen.getByText('题目内容')).toBeInTheDocument();
    expect(screen.getByText('解析')).toBeInTheDocument();
    expect(screen.getByText('标签')).toBeInTheDocument();
    expect(screen.getByText('知识点分类')).toBeInTheDocument();
  });

  it('创建模式默认题型为单选，应显示选项区域', () => {
    render(
      <QuestionFormDrawer
        open={true}
        mode="create"
        categoryTree={emptyTree}
        onSubmit={jest.fn()}
        onClose={jest.fn()}
      />,
    );
    expect(screen.getByText('选项 A')).toBeInTheDocument();
    expect(screen.getByText('选项 B')).toBeInTheDocument();
    expect(screen.getByText('正确答案')).toBeInTheDocument();
  });

  it('编辑模式下题型 Select 应禁用', () => {
    render(
      <QuestionFormDrawer
        open={true}
        mode="edit"
        initialValues={{
          id: 1,
          type: 'SINGLE_CHOICE',
          content: 'Test',
          options: [{ key: 'A', content: 'A' }, { key: 'B', content: 'B' }],
          answer: 'A',
          explanation: '',
          difficulty: 1,
          tags: [],
          status: 'ACTIVE',
          categoryIds: [],
          createdAt: 0,
          updatedAt: 0,
        }}
        categoryTree={emptyTree}
        onSubmit={jest.fn()}
        onClose={jest.fn()}
      />,
    );
    const typeSelect = document.querySelector('#type');
    expect(typeSelect).toBeInTheDocument();
    // 编辑模式下题型应不可选（disabled）
    expect(screen.getByText('单选')).toBeInTheDocument();
  });

  it('编辑模式标题为"编辑题目"', () => {
    render(
      <QuestionFormDrawer
        open={true}
        mode="create"
        categoryTree={emptyTree}
        onSubmit={jest.fn()}
        onClose={jest.fn()}
      />,
    );
    expect(screen.getByText('新建题目')).toBeInTheDocument();
  });
});

describe('QuestionFormDrawer — 提交编排', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('编辑提交应调用 updateQuestion，分类未变时跳过 updateQuestionCategories', async () => {
    mockUpdateQuestion.mockResolvedValue({ id: 1 });
    mockUpdateQuestionCategories.mockResolvedValue(undefined);
    const user = userEvent.setup();
    const onSubmit = jest.fn();

    render(
      <QuestionFormDrawer
        open={true}
        mode="edit"
        initialValues={{
          id: 1,
          type: 'TRUE_FALSE',
          content: 'Is Java compiled?',
          options: null,
          answer: 'true',
          explanation: '',
          difficulty: 2,
          tags: [],
          status: 'ACTIVE',
          categoryIds: [1],
          createdAt: 0,
          updatedAt: 0,
        }}
        categoryTree={emptyTree}
        onSubmit={onSubmit}
        onClose={jest.fn()}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText('对')).toBeInTheDocument();
    });

    await user.click(screen.getByText('提 交'));

    await waitFor(() => {
      expect(mockUpdateQuestion).toHaveBeenCalledTimes(1);
      // 分类未变更，不应调用 updateQuestionCategories
      expect(mockUpdateQuestionCategories).not.toHaveBeenCalled();
    });
    expect(onSubmit).toHaveBeenCalled();
  });

  it('创建提交失败时不应关闭 Drawer', async () => {
    mockCreateQuestion.mockRejectedValue(new Error('后端错误'));
    const user = userEvent.setup();
    const onSubmit = jest.fn();

    render(
      <QuestionFormDrawer
        open={true}
        mode="create"
        categoryTree={emptyTree}
        onSubmit={onSubmit}
        onClose={jest.fn()}
      />,
    );

    // 填满必填字段（包括选项内容）后提交
    const contentInput = screen.getByPlaceholderText('请输入题目内容');
    await user.type(contentInput, 'Test question');
    // 填选项内容以满足校验
    const optionInputs = screen.getAllByPlaceholderText(/选项 . 内容/);
    await user.type(optionInputs[0], 'Option A content');
    await user.type(optionInputs[1], 'Option B content');
    // 选答案
    const radio = screen.getByRole('radio', { name: /A\. Option A content/ });
    await user.click(radio);

    await user.click(screen.getByText('提 交'));

    await waitFor(() => {
      expect(mockCreateQuestion).toHaveBeenCalled();
    });
    // 提交失败不应调用 onSubmit
    expect(onSubmit).not.toHaveBeenCalled();
  });
});

describe('parseAnswer — 容错与反序列化', () => {
  it('单选：直接返回字符串', () => {
    expect(parseAnswer('SINGLE_CHOICE', 'A')).toBe('A');
  });

  it('多选：正确解析 JSON 数组', () => {
    expect(parseAnswer('MULTIPLE_CHOICE', '["A","C"]')).toEqual(['A', 'C']);
  });

  it('多选：畸形 JSON 时降级为单元素数组', () => {
    expect(parseAnswer('MULTIPLE_CHOICE', 'not-json')).toEqual(['not-json']);
  });

  it('判断：正确解析 true/false 字符串', () => {
    expect(parseAnswer('TRUE_FALSE', 'true')).toBe(true);
    expect(parseAnswer('TRUE_FALSE', 'false')).toBe(false);
  });

  it('填空：正确解析 JSON 数组', () => {
    expect(parseAnswer('FILL_BLANK', '["k1","k2"]')).toEqual(['k1', 'k2']);
  });

  it('填空：畸形 JSON 时降级为单元素数组', () => {
    expect(parseAnswer('FILL_BLANK', 'broken')).toEqual(['broken']);
  });
});

describe('normalizeOptionsAndAnswer — 选项 key 规范化', () => {
  it('应保留已排序的 key', () => {
    const { options } = normalizeOptionsAndAnswer(
      [
        { key: 'A', content: 'a' },
        { key: 'B', content: 'b' },
      ],
      'A',
      'SINGLE_CHOICE',
    );
    expect(options[0].key).toBe('A');
    expect(options[1].key).toBe('B');
  });

  it('应重新分配错乱的 key', () => {
    const { options } = normalizeOptionsAndAnswer(
      [
        { key: 'A', content: 'a' },
        { key: 'C', content: 'c' }, // key 错乱，应为 B
        { key: 'D', content: 'd' }, // key 错乱，应为 C
      ],
      'C',
      'SINGLE_CHOICE',
    );
    expect(options.map((o) => o.key)).toEqual(['A', 'B', 'C']);
  });

  it('单选答案 key 应跟随选项 key 重排而映射', () => {
    const { answer, options } = normalizeOptionsAndAnswer(
      [
        { key: 'A', content: 'a' },
        { key: 'C', content: 'c' }, // 将为 B
      ],
      'C', // 选了旧 key=C 的选项
      'SINGLE_CHOICE',
    );
    expect(options[1].key).toBe('B');
    expect(answer).toBe('B'); // 答案从 C 映射为 B
  });

  it('多选答案 key 应全部跟随映射', () => {
    const { answer } = normalizeOptionsAndAnswer(
      [
        { key: 'X', content: 'a' },
        { key: 'Y', content: 'b' },
        { key: 'Z', content: 'c' },
      ],
      ['X', 'Z'],
      'MULTIPLE_CHOICE',
    );
    expect(answer).toEqual(['A', 'C']); // X→A, Z→C
  });

  it('空选项应直接返回空数组', () => {
    const { options, answer } = normalizeOptionsAndAnswer(
      undefined,
      'A',
      'SINGLE_CHOICE',
    );
    expect(options).toEqual([]);
    expect(answer).toBe('A');
  });
});

describe('QuestionFormDrawer — 题型切换 UX', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('切换到判断：应隐藏选项，显示对/错', async () => {
    const user = userEvent.setup();
    render(
      <QuestionFormDrawer
        open={true}
        mode="create"
        categoryTree={[]}
        onSubmit={jest.fn()}
        onClose={jest.fn()}
      />,
    );
    // 默认单选，有选项 A/B
    expect(screen.getByText('选项 A')).toBeInTheDocument();

    // 切换到判断
    const typeSelect = screen.getByRole('combobox', { name: '题型' });
    await user.click(typeSelect);
    await user.click(screen.getByText('判断'));

    // 选项应消失
    expect(screen.queryByText('选项 A')).not.toBeInTheDocument();
    // 对/错答案出现
    expect(screen.getByText('对')).toBeInTheDocument();
    expect(screen.getByText('错')).toBeInTheDocument();
  });

  it('切换到填空：应隐藏选项，显示关键词列表', async () => {
    const user = userEvent.setup();
    render(
      <QuestionFormDrawer
        open={true}
        mode="create"
        categoryTree={[]}
        onSubmit={jest.fn()}
        onClose={jest.fn()}
      />,
    );
    const typeSelect = screen.getByRole('combobox', { name: '题型' });
    await user.click(typeSelect);
    await user.click(screen.getByText('填空'));

    expect(screen.queryByText('选项 A')).not.toBeInTheDocument();
    expect(screen.getByText('新增关键词')).toBeInTheDocument();
  });

  it('多选切回单选：答案应保留首元素', async () => {
    const user = userEvent.setup();
    render(
      <QuestionFormDrawer
        open={true}
        mode="create"
        categoryTree={[]}
        onSubmit={jest.fn()}
        onClose={jest.fn()}
      />,
    );

    let typeSelect = screen.getByRole('combobox', { name: '题型' });
    await user.click(typeSelect);
    await user.click(screen.getByText('多选'));

    const inputs = screen.getAllByPlaceholderText(/选项 . 内容/);
    await user.type(inputs[0], 'Opt A');
    await user.type(inputs[1], 'Opt B');

    const checkboxes = screen.getAllByRole('checkbox');
    await user.click(checkboxes[0]);
    await user.click(checkboxes[1]);

    typeSelect = screen.getByRole('combobox', { name: '题型' });
    await user.click(typeSelect);
    await user.click(screen.getByText('单选'));

    expect(screen.getByText('选项 A')).toBeInTheDocument();
  });

  it('判断切回单选：选项应补默认 A/B', async () => {
    const user = userEvent.setup();
    render(
      <QuestionFormDrawer
        open={true}
        mode="create"
        categoryTree={[]}
        onSubmit={jest.fn()}
        onClose={jest.fn()}
      />,
    );

    // 切到判断（选项清空）
    let typeSelect = screen.getByRole('combobox', { name: '题型' });
    await user.click(typeSelect);
    await user.click(screen.getByText('判断'));
    expect(screen.queryByText('选项 A')).not.toBeInTheDocument();

    // 切回单选（选项应补默认 A/B）
    typeSelect = screen.getByRole('combobox', { name: '题型' });
    await user.click(typeSelect);
    await user.click(screen.getByText('单选'));

    expect(screen.getByText('选项 A')).toBeInTheDocument();
    expect(screen.getByText('选项 B')).toBeInTheDocument();
    // 答案应被清空
    const radioInputs = screen.getAllByRole('radio');
    const anyChecked = radioInputs.some((r) => (r as HTMLInputElement).checked);
    expect(anyChecked).toBe(false);
  });
});

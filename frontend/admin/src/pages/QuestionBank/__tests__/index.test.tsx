/**
 * 题库管理页测试
 *
 * 未覆盖场景（降级为手动验证，见 PRD:352-363）：
 * - 批量启用/停用：ProTable checkbox → tableAlertOptionRender 渲染链路在 jsdom 下脆弱
 * - 导入流程：Modal.confirm + setTimeout 时序在测试环境不稳定
 */

import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

// mock 服务层
const mockListQuestions = jest.fn();
const mockGetQuestionById = jest.fn();
const mockBatchActivate = jest.fn();
const mockBatchDeactivate = jest.fn();
const mockDownloadImportTemplate = jest.fn();
const mockImportQuestions = jest.fn();

jest.mock('@/services/questionBank', () => ({
  ...jest.requireActual('@/services/questionBank'),
  listQuestions: (...args: any[]) => mockListQuestions(...args),
  getQuestionById: (...args: any[]) => mockGetQuestionById(...args),
  batchActivate: (...args: any[]) => mockBatchActivate(...args),
  batchDeactivate: (...args: any[]) => mockBatchDeactivate(...args),
  downloadImportTemplate: (...args: any[]) => mockDownloadImportTemplate(...args),
  importQuestions: (...args: any[]) => mockImportQuestions(...args),
}));

const mockGetTree = jest.fn();

jest.mock('@/services/knowledge-category', () => ({
  ...jest.requireActual('@/services/knowledge-category'),
  getTree: (...args: any[]) => mockGetTree(...args),
}));

// mock antd message（使用 __esModule 保持兼容性）
jest.mock('antd', () => {
  const actual = jest.requireActual('antd');
  return {
    __esModule: true,
    ...actual,
    message: {
      success: jest.fn(),
      error: jest.fn(),
      warning: jest.fn(),
    },
  };
});

import QuestionBank from '../index';

/** 构造模拟题目列表响应 */
function mockPageResult(overrides?: any) {
  return {
    content: [
      {
        id: 1,
        type: 'SINGLE_CHOICE',
        content: 'What is Java?',
        options: [
          { key: 'A', content: 'Language' },
          { key: 'B', content: 'Coffee' },
        ],
        answer: 'A',
        explanation: '',
        difficulty: 1,
        tags: ['basic'],
        status: 'ACTIVE',
        categoryIds: [2],
        createdAt: 1700000000000,
        updatedAt: 1700000000000,
      },
      {
        id: 2,
        type: 'TRUE_FALSE',
        content: 'Java is a compiled language.',
        options: null,
        answer: 'true',
        explanation: '',
        difficulty: 2,
        tags: [],
        status: 'INACTIVE',
        categoryIds: [],
        createdAt: 1700000000000,
        updatedAt: 1700000000000,
      },
    ],
    totalElements: 2,
    pageNumber: 0,
    pageSize: 20,
    totalPages: 1,
    ...overrides,
  };
}

describe('QuestionBank 列表页', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockGetTree.mockResolvedValue([
      {
        id: 1,
        parentId: null,
        name: 'Root',
        status: 'ACTIVE',
        iconUrl: null,
        color: null,
        sortOrder: 0,
        children: [
          {
            id: 2,
            parentId: 1,
            name: 'Java',
            status: 'ACTIVE',
            iconUrl: null,
            color: null,
            sortOrder: 0,
          },
        ],
      },
    ]);
    mockListQuestions.mockResolvedValue(mockPageResult());
  });

  it('应渲染 ProTable 标题和工具栏按钮', async () => {
    render(<QuestionBank />);
    await waitFor(() => {
      expect(screen.getByText('题库管理')).toBeInTheDocument();
    });
    expect(screen.getByText('新建题目')).toBeInTheDocument();
    expect(screen.getByText('下载模板')).toBeInTheDocument();
    expect(screen.getByText('批量导入')).toBeInTheDocument();
  });

  it('应在挂载时调用一次 getTree', async () => {
    render(<QuestionBank />);
    await waitFor(() => {
      expect(mockGetTree).toHaveBeenCalledTimes(1);
    });
  });

  it('点击新建题目应打开 Drawer', async () => {
    const user = userEvent.setup();
    render(<QuestionBank />);
    await waitFor(() => {
      expect(screen.getByText('新建题目')).toBeInTheDocument();
    });
    const createBtn = document.querySelector('.btn-create-question');
    expect(createBtn).toBeInTheDocument();
    await user.click(createBtn!);
    // Drawer 打开后，ant-drawer-title 中会显示"新建题目"
    await waitFor(() => {
      const drawerTitles = document.querySelectorAll('.ant-drawer-title');
      expect(drawerTitles.length).toBeGreaterThan(0);
    });
  });

  it('单条停用操作应调用 batchDeactivate', async () => {
    const user = userEvent.setup();
    mockBatchDeactivate.mockResolvedValue(undefined);
    render(<QuestionBank />);
    await waitFor(() => {
      expect(screen.getByText('What is Java?')).toBeInTheDocument();
    });
    const deactivateLinks = screen.getAllByText('停用');
    await user.click(deactivateLinks[0]);
    await waitFor(() => {
      const confirmBtn = document.querySelector('.ant-popconfirm .ant-btn-primary');
      expect(confirmBtn).toBeInTheDocument();
    });
    const confirmBtn = document.querySelector('.ant-popconfirm .ant-btn-primary')!;
    (confirmBtn as HTMLButtonElement).click();
    await waitFor(() => {
      expect(mockBatchDeactivate).toHaveBeenCalledWith([1]);
    });
  });

  it('排序切换应触发 listQuestions 重新请求', async () => {
    const user = userEvent.setup();
    mockListQuestions.mockResolvedValue(mockPageResult());
    render(<QuestionBank />);

    // 等待初始加载
    await waitFor(() => {
      expect(mockListQuestions).toHaveBeenCalled();
    });
    const initialCalls = mockListQuestions.mock.calls.length;

    // 切换 sort 为"创建时间"
    const sortSelect = document.querySelector('.ant-select');
    expect(sortSelect).toBeInTheDocument();

    const orderBtn = screen.getByText('降序');
    await user.click(orderBtn);

    await waitFor(() => {
      expect(mockListQuestions.mock.calls.length).toBeGreaterThan(initialCalls);
    });
  });

  it('点击编辑应调 getQuestionById 并打开 Drawer', async () => {
    const user = userEvent.setup();
    const mockDetail = mockPageResult().content[0];
    mockGetQuestionById.mockResolvedValue(mockDetail);

    render(<QuestionBank />);
    await waitFor(() => {
      expect(screen.getByText('What is Java?')).toBeInTheDocument();
    });

    // 点击第一条的"编辑"
    const editLinks = screen.getAllByText('编辑');
    await user.click(editLinks[0]);

    await waitFor(() => {
      expect(mockGetQuestionById).toHaveBeenCalledWith(1);
    });
    // Drawer 应打开
    await waitFor(() => {
      const drawerTitles = document.querySelectorAll('.ant-drawer-title');
      expect(drawerTitles.length).toBeGreaterThan(0);
    });
  });

  it('点击下载模板应调用 downloadImportTemplate', async () => {
    const user = userEvent.setup();
    const mockBlob = new Blob(['test'], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    mockDownloadImportTemplate.mockResolvedValue(mockBlob);

    render(<QuestionBank />);
    await waitFor(() => {
      expect(screen.getByText('下载模板')).toBeInTheDocument();
    });

    await user.click(screen.getByText('下载模板'));

    await waitFor(() => {
      expect(mockDownloadImportTemplate).toHaveBeenCalled();
    });
  });
});

import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

/** 模拟服务模块 */
jest.mock('@/services/ipSeries', () => ({
  listIpSeries: jest.fn(),
  createIpSeries: jest.fn(),
  updateIpSeries: jest.fn(),
  deleteIpSeries: jest.fn(),
}));

jest.mock('@/services/fileUpload', () => ({
  getUploadCredential: jest.fn(),
  uploadFile: jest.fn(),
}));

jest.mock('@/utils/token', () => ({
  getUserInfo: jest.fn(() => ({ id: 1, username: 'admin', nickname: '管理员', role: 'ADMIN' })),
}));

/** 模拟 antd message */
jest.mock('antd', () => {
  const actual = jest.requireActual('antd');
  return {
    ...actual,
    message: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
  };
});

import { message } from 'antd';
import {
  createIpSeries,
  listIpSeries,
  updateIpSeries,
} from '@/services/ipSeries';
import { getUploadCredential, uploadFile } from '@/services/fileUpload';
import IpSeries from '../index';

/** 构造模拟分页结果 */
function mockPageResult(content: unknown[]) {
  return {
    content,
    totalElements: content.length,
    pageNumber: 0,
    pageSize: 20,
    totalPages: 1,
  };
}

function mockIpRecord(overrides = {}) {
  return {
    id: 1,
    code: 'IP001',
    name: '测试 IP 系列',
    description: '测试描述',
    coverImageUrl: 'http://localhost:8083/static/cover.png',
    status: 'ACTIVE',
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-02T00:00:00',
    ...overrides,
  };
}

beforeEach(() => {
  jest.clearAllMocks();
});

/** ProTable 工具栏中带 className 的按钮即为"新建" */
function getCreateButton(): HTMLElement {
  const btn = document.querySelector('.btn-create-ip-series') as HTMLElement;
  if (!btn) throw new Error('找不到新建按钮');
  return btn;
}

describe('IpSeries 渲染', () => {
  it('应渲染 ProTable 并显示列表数据', async () => {
    (listIpSeries as jest.Mock).mockResolvedValue(
      mockPageResult([mockIpRecord(), mockIpRecord({ id: 2, code: 'IP002', name: '第二个 IP' })]),
    );

    render(<IpSeries />);

    await waitFor(() => {
      expect(screen.getByText('测试 IP 系列')).toBeInTheDocument();
    });
    expect(screen.getByText('IP001')).toBeInTheDocument();
    expect(screen.getByText('第二个 IP')).toBeInTheDocument();
  });

  it('工具栏应包含按钮', async () => {
    (listIpSeries as jest.Mock).mockResolvedValue(mockPageResult([]));

    render(<IpSeries />);

    await waitFor(() => {
      const buttons = screen.getAllByRole('button');
      expect(buttons.length).toBeGreaterThan(0);
    });
  });

  it('状态列应为带颜色的 Tag 组件', async () => {
    (listIpSeries as jest.Mock).mockResolvedValue(
      mockPageResult([
        mockIpRecord({ status: 'ACTIVE' }),
        mockIpRecord({ id: 2, code: 'IP002', name: '停用的 IP', status: 'INACTIVE' }),
      ]),
    );

    render(<IpSeries />);

    await waitFor(() => {
      // "启用"出现在搜索栏下拉 + ACTIVE Tag 中；"停用"出现在 INACTIVE Tag + 操作列链接中
      expect(screen.getAllByText('启用').length).toBeGreaterThanOrEqual(2);
      expect(screen.getAllByText('停用').length).toBeGreaterThanOrEqual(2);
    });

    // 验证 Tag 颜色
    const greenTag = document.querySelector('.ant-tag-green');
    const defaultTag = document.querySelector('.ant-tag-default');
    expect(greenTag).toBeInTheDocument();
    expect(defaultTag).toBeInTheDocument();
  });

  it('空列表应正常渲染', async () => {
    (listIpSeries as jest.Mock).mockResolvedValue(mockPageResult([]));

    render(<IpSeries />);

    await waitFor(() => {
      expect(screen.getByText('IP 系列管理')).toBeInTheDocument();
    });
  });
});

describe('创建 IP 系列', () => {
  it('点击新建按钮应打开弹窗', async () => {
    (listIpSeries as jest.Mock).mockResolvedValue(mockPageResult([]));

    render(<IpSeries />);

    await waitFor(() => {
      expect(screen.getByText('IP 系列管理')).toBeInTheDocument();
    });

    fireEvent.click(getCreateButton());

    await waitFor(() => {
      expect(screen.getByText('新建 IP 系列')).toBeInTheDocument();
    });
  });

  it('填写表单并提交应调用 createIpSeries', async () => {
    (listIpSeries as jest.Mock).mockResolvedValue(mockPageResult([]));
    (createIpSeries as jest.Mock).mockResolvedValue(mockIpRecord({ id: 99, code: 'NEW', name: '新系列' }));

    render(<IpSeries />);

    await waitFor(() => {
      expect(screen.getByText('IP 系列管理')).toBeInTheDocument();
    });

    // 点击新建按钮打开弹窗
    fireEvent.click(getCreateButton());

    await waitFor(() => {
      expect(screen.getByText('新建 IP 系列')).toBeInTheDocument();
    });

    // 填写表单
    fireEvent.change(screen.getByPlaceholderText('请输入编码'), { target: { value: 'NEW' } });
    fireEvent.change(screen.getByPlaceholderText('请输入名称'), { target: { value: '新系列' } });
    fireEvent.change(screen.getByPlaceholderText('请输入描述'), { target: { value: '描述文本' } });

    // 提交（弹窗中的提交按钮）
    const modal = document.querySelector('.ant-modal')!;
    const submitBtn = modal.querySelector('.ant-btn-primary') as HTMLElement;
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(createIpSeries).toHaveBeenCalledWith(
        expect.objectContaining({
          code: 'NEW',
          name: '新系列',
          description: '描述文本',
          status: 'ACTIVE',
        }),
      );
      expect(message.success).toHaveBeenCalledWith('创建成功');
    });
  });

  it('创建失败应显示错误信息', async () => {
    (listIpSeries as jest.Mock).mockResolvedValue(mockPageResult([]));
    (createIpSeries as jest.Mock).mockRejectedValue(new Error('编码已存在'));

    render(<IpSeries />);

    await waitFor(() => {
      expect(screen.getByText('IP 系列管理')).toBeInTheDocument();
    });

    fireEvent.click(getCreateButton());

    await waitFor(() => {
      expect(screen.getByText('新建 IP 系列')).toBeInTheDocument();
    });

    fireEvent.change(screen.getByPlaceholderText('请输入编码'), { target: { value: 'NEW' } });
    fireEvent.change(screen.getByPlaceholderText('请输入名称'), { target: { value: '新系列' } });

    const modal = document.querySelector('.ant-modal')!;
    const submitBtn = modal.querySelector('.ant-btn-primary') as HTMLElement;
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(message.error).toHaveBeenCalledWith('编码已存在');
    });
  });
});

describe('编辑 IP 系列', () => {
  it('点击编辑应打开弹窗并预填数据', async () => {
    (listIpSeries as jest.Mock).mockResolvedValue(
      mockPageResult([mockIpRecord()]),
    );

    render(<IpSeries />);

    await waitFor(() => {
      expect(screen.getByText('测试 IP 系列')).toBeInTheDocument();
    });

    // 点击"编辑"链接
    fireEvent.click(screen.getByText('编辑'));

    await waitFor(() => {
      expect(screen.getByText('编辑 IP 系列')).toBeInTheDocument();
    });

    // 验证表单预填了数据
    const codeInput = screen.getByPlaceholderText('请输入编码') as HTMLInputElement;
    const nameInput = screen.getByPlaceholderText('请输入名称') as HTMLInputElement;
    expect(codeInput.value).toBe('IP001');
    expect(nameInput.value).toBe('测试 IP 系列');
  });

  it('修改后提交应调用 updateIpSeries', async () => {
    (listIpSeries as jest.Mock).mockResolvedValue(
      mockPageResult([mockIpRecord()]),
    );
    (updateIpSeries as jest.Mock).mockResolvedValue(
      mockIpRecord({ id: 1, name: '改名后的 IP' }),
    );

    render(<IpSeries />);

    await waitFor(() => {
      expect(screen.getByText('编辑')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('编辑'));

    await waitFor(() => {
      expect(screen.getByText('编辑 IP 系列')).toBeInTheDocument();
    });

    // 修改名称
    const nameInput = screen.getByPlaceholderText('请输入名称');
    fireEvent.change(nameInput, { target: { value: '改名后的 IP' } });

    // 提交
    const modal = document.querySelector('.ant-modal')!;
    const submitBtn = modal.querySelector('.ant-btn-primary') as HTMLElement;
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(updateIpSeries).toHaveBeenCalledWith(
        1,
        expect.objectContaining({
          name: '改名后的 IP',
          coverImageUrl: expect.any(String),
        }),
      );
      expect(message.success).toHaveBeenCalledWith('更新成功');
    });
  });

  it('编辑失败应显示错误信息', async () => {
    (listIpSeries as jest.Mock).mockResolvedValue(
      mockPageResult([mockIpRecord()]),
    );
    (updateIpSeries as jest.Mock).mockRejectedValue(new Error('编码已存在'));

    render(<IpSeries />);

    await waitFor(() => {
      expect(screen.getByText('编辑')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('编辑'));

    await waitFor(() => {
      expect(screen.getByText('编辑 IP 系列')).toBeInTheDocument();
    });

    const modal = document.querySelector('.ant-modal')!;
    const submitBtn = modal.querySelector('.ant-btn-primary') as HTMLElement;
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(message.error).toHaveBeenCalledWith('编码已存在');
    });
  });
});

describe('切换启用/停用状态', () => {
  it('启用中的 IP 系列显示"停用"操作', async () => {
    (listIpSeries as jest.Mock).mockResolvedValue(
      mockPageResult([mockIpRecord({ status: 'ACTIVE' })]),
    );

    render(<IpSeries />);

    await waitFor(() => {
      expect(screen.getByText('停用')).toBeInTheDocument();
    });
  });

  it('停用中的 IP 系列显示"启用"操作', async () => {
    (listIpSeries as jest.Mock).mockResolvedValue(
      mockPageResult([mockIpRecord({ id: 2, name: '已停用 IP', status: 'INACTIVE' })]),
    );

    render(<IpSeries />);

    await waitFor(() => {
      expect(screen.getByText('启用')).toBeInTheDocument();
    });
  });

  it('点击"停用"应弹出 Popconfirm 并确认后调用 updateIpSeries', async () => {
    (listIpSeries as jest.Mock).mockResolvedValue(
      mockPageResult([mockIpRecord({ status: 'ACTIVE' })]),
    );
    (updateIpSeries as jest.Mock).mockResolvedValue(
      mockIpRecord({ id: 1, status: 'INACTIVE' }),
    );

    render(<IpSeries />);

    await waitFor(() => {
      expect(screen.getByText('停用')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('停用'));

    await waitFor(() => {
      expect(screen.getByText('确定停用该 IP 系列吗？')).toBeInTheDocument();
    });

    const popconfirm = document.querySelector('.ant-popconfirm')!;
    const confirmBtn = popconfirm.querySelector('.ant-btn-primary') as HTMLElement;
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(updateIpSeries).toHaveBeenCalledWith(1, { status: 'INACTIVE' });
      expect(message.success).toHaveBeenCalledWith('已停用');
    });
  });

  it('点击"启用"应调用 updateIpSeries 设置为 ACTIVE', async () => {
    (listIpSeries as jest.Mock).mockResolvedValue(
      mockPageResult([mockIpRecord({ id: 2, name: '已停用 IP', status: 'INACTIVE' })]),
    );
    (updateIpSeries as jest.Mock).mockResolvedValue(
      mockIpRecord({ id: 2, status: 'ACTIVE' }),
    );

    render(<IpSeries />);

    await waitFor(() => {
      expect(screen.getByText('已停用 IP')).toBeInTheDocument();
    });

    // "启用"可能出现在多个位置（搜索栏下拉 + 操作列链接），取操作列中的 <a> 元素
    const enableElements = screen.getAllByText('启用');
    const actionLink = enableElements.find((el) => el.tagName === 'A') as HTMLElement;
    fireEvent.click(actionLink);

    await waitFor(() => {
      expect(screen.getByText('确定启用该 IP 系列吗？')).toBeInTheDocument();
    });

    const popconfirm = document.querySelector('.ant-popconfirm')!;
    const confirmBtn = popconfirm.querySelector('.ant-btn-primary') as HTMLElement;
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(updateIpSeries).toHaveBeenCalledWith(2, { status: 'ACTIVE' });
      expect(message.success).toHaveBeenCalledWith('已启用');
    });
  });

  it('切换失败应显示错误信息', async () => {
    (listIpSeries as jest.Mock).mockResolvedValue(
      mockPageResult([mockIpRecord({ status: 'ACTIVE' })]),
    );
    (updateIpSeries as jest.Mock).mockRejectedValue(
      new Error('该 IP 下已有卡牌，不允许停用'),
    );

    render(<IpSeries />);

    await waitFor(() => {
      expect(screen.getByText('停用')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('停用'));

    await waitFor(() => {
      expect(screen.getByText('确定停用该 IP 系列吗？')).toBeInTheDocument();
    });

    const popconfirm = document.querySelector('.ant-popconfirm')!;
    const confirmBtn = popconfirm.querySelector('.ant-btn-primary') as HTMLElement;
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(message.error).toHaveBeenCalledWith('该 IP 下已有卡牌，不允许停用');
    });
  });
});

describe('封面图上传', () => {
  it('打开弹窗时应显示上传区域', async () => {
    (listIpSeries as jest.Mock).mockResolvedValue(mockPageResult([]));

    render(<IpSeries />);

    await waitFor(() => {
      expect(screen.getByText('IP 系列管理')).toBeInTheDocument();
    });

    fireEvent.click(getCreateButton());

    await waitFor(() => {
      // 弹窗中应渲染 Upload 上传组件
      const modal = document.querySelector('.ant-modal')!;
      expect(modal.querySelector('.ant-upload')).toBeInTheDocument();
    });
  });

  it('选择非图片文件应校验失败', async () => {
    (listIpSeries as jest.Mock).mockResolvedValue(mockPageResult([]));

    render(<IpSeries />);

    await waitFor(() => {
      expect(screen.getByText('IP 系列管理')).toBeInTheDocument();
    });

    fireEvent.click(getCreateButton());

    // 等弹窗渲染
    await waitFor(() => {
      expect(document.querySelector('.ant-modal')).toBeInTheDocument();
    });

    // 通过 Upload 组件找到隐藏的 file input
    const fileInput = document.querySelector('.ant-upload input[type="file"]') as HTMLInputElement;
    expect(fileInput).toBeInTheDocument();

    // 模拟选择一个非图片文件
    const invalidFile = new File(['content'], 'test.txt', { type: 'text/plain' });
    fireEvent.change(fileInput, { target: { files: [invalidFile] } });

    await waitFor(() => {
      expect(message.error).toHaveBeenCalledWith('仅支持 JPG、PNG、GIF、WebP 格式');
    });
    // 不应调用上传凭证接口
    expect(getUploadCredential).not.toHaveBeenCalled();
  });

  it('选择超限文件应校验失败', async () => {
    (listIpSeries as jest.Mock).mockResolvedValue(mockPageResult([]));

    render(<IpSeries />);

    await waitFor(() => {
      expect(screen.getByText('IP 系列管理')).toBeInTheDocument();
    });

    fireEvent.click(getCreateButton());

    await waitFor(() => {
      expect(document.querySelector('.ant-modal')).toBeInTheDocument();
    });

    const fileInput = document.querySelector('.ant-upload input[type="file"]') as HTMLInputElement;
    expect(fileInput).toBeInTheDocument();

    // 模拟一个超大文件（11MB）
    const hugeFile = new File(['x'.repeat(11 * 1024 * 1024)], 'huge.png', { type: 'image/png' });
    fireEvent.change(fileInput, { target: { files: [hugeFile] } });

    await waitFor(() => {
      expect(message.error).toHaveBeenCalledWith('文件大小不能超过 10MB');
    });
    expect(getUploadCredential).not.toHaveBeenCalled();
  });
});

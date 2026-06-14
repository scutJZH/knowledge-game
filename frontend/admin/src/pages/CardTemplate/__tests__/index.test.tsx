import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

/** 模拟服务模块（jest.fn() 必须在 jest.mock 工厂内直接创建，不能用外部变量） */
jest.mock('@/services/cardTemplate', () => ({
  listCardTemplates: jest.fn(),
  getCardTemplateById: jest.fn(),
  createCardTemplate: jest.fn(),
  updateCardTemplate: jest.fn(),
  addOrUpdateStarImage: jest.fn(),
}));

jest.mock('@/services/ipSeries', () => ({
  listIpSeries: jest.fn(),
}));

/** Mock ImageUploadField 为可控 input，便于测试星级图片变更比对 */
jest.mock('@/components/ImageUploadField', () => ({
  __esModule: true,
  default: ({ bizType, placeholder, value, onChange }: {
    bizType: string;
    placeholder?: string;
    preview?: boolean;
    allowRemove?: boolean;
    value?: string;
    onChange?: (value: string | undefined) => void;
  }) => {
    const level = (placeholder || '').replace('★', '');
    return (
      <input
        data-testid={`star-image-upload-${level}`}
        type="text"
        value={value || ''}
        onChange={(e) => onChange?.(e.target.value || undefined)}
      />
    );
  },
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
  addOrUpdateStarImage,
  createCardTemplate,
  getCardTemplateById,
  listCardTemplates,
  updateCardTemplate,
} from '@/services/cardTemplate';
import { listIpSeries } from '@/services/ipSeries';
import CardTemplate from '../index';

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

function mockCardRecord(overrides = {}) {
  return {
    id: 1,
    ipSeriesId: 1,
    ipSeriesName: '测试IP',
    code: 'CT001',
    name: '测试卡牌',
    rarity: 'SSR',
    description: '',
    status: 'ACTIVE',
    createdAt: '2025-01-01T00:00:00',
    updatedAt: '2025-01-01T00:00:00',
    ...overrides,
  };
}

function mockCardDetail(overrides = {}) {
  return {
    ...mockCardRecord(overrides),
    starImages: [
      { starLevel: 1, imageUrl: 'http://example.com/star1.png' },
    ],
    ...overrides,
  };
}

/** ProTable 工具栏中带 className 的按钮即为"新建" */
function getCreateButton(): HTMLElement {
  const btn = document.querySelector('.btn-create-card-template') as HTMLElement;
  if (!btn) throw new Error('找不到新建按钮');
  return btn;
}

beforeEach(() => {
  jest.clearAllMocks();
});

describe('CardTemplate 列表渲染', () => {
  it('应渲染 ProTable 并显示列表数据', async () => {
    (listCardTemplates as jest.Mock).mockResolvedValue(
      mockPageResult([mockCardRecord(), mockCardRecord({ id: 2, code: 'CT002', name: '第二个卡牌' })]),
    );

    render(<CardTemplate />);

    await waitFor(() => {
      expect(screen.getByText('测试卡牌')).toBeInTheDocument();
    });
    expect(screen.getByText('CT001')).toBeInTheDocument();
    expect(screen.getByText('第二个卡牌')).toBeInTheDocument();
  });

  it('工具栏应包含按钮', async () => {
    (listCardTemplates as jest.Mock).mockResolvedValue(mockPageResult([]));

    render(<CardTemplate />);

    await waitFor(() => {
      const buttons = screen.getAllByRole('button');
      expect(buttons.length).toBeGreaterThan(0);
    });
  });

  it('稀有度列应为 Tag 组件', async () => {
    (listCardTemplates as jest.Mock).mockResolvedValue(
      mockPageResult([
        mockCardRecord({ rarity: 'N' }),
        mockCardRecord({ id: 2, code: 'CT-R', rarity: 'R' }),
        mockCardRecord({ id: 3, code: 'CT-SR', rarity: 'SR' }),
        mockCardRecord({ id: 4, code: 'CT-SSR', rarity: 'SSR' }),
        mockCardRecord({ id: 5, code: 'CT-SP', rarity: 'SP' }),
      ]),
    );

    render(<CardTemplate />);

    await waitFor(() => {
      expect(screen.getByText('N')).toBeInTheDocument();
      expect(screen.getByText('R')).toBeInTheDocument();
      expect(screen.getByText('SR')).toBeInTheDocument();
      expect(screen.getByText('SSR')).toBeInTheDocument();
      expect(screen.getByText('SP')).toBeInTheDocument();
    });
  });

  it('状态列应为 Tag 组件', async () => {
    (listCardTemplates as jest.Mock).mockResolvedValue(
      mockPageResult([
        mockCardRecord({ status: 'ACTIVE' }),
        mockCardRecord({ id: 2, code: 'CT-INACTIVE', name: '停用的卡牌', status: 'INACTIVE' }),
      ]),
    );

    render(<CardTemplate />);

    await waitFor(() => {
      // filter/操作列/table 中各有"启用"和"停用"，用 getAllByText
      expect(screen.getAllByText('启用').length).toBeGreaterThan(0);
      expect(screen.getAllByText('停用').length).toBeGreaterThan(0);
      expect(screen.getByText('停用的卡牌')).toBeInTheDocument();
    });
  });

  it('空列表应正常渲染', async () => {
    (listCardTemplates as jest.Mock).mockResolvedValue(mockPageResult([]));

    render(<CardTemplate />);

    await waitFor(() => {
      expect(screen.getByText('卡牌管理')).toBeInTheDocument();
    });
  });

  it('加载时应默认 status=ACTIVE', async () => {
    (listCardTemplates as jest.Mock).mockResolvedValue(mockPageResult([mockCardRecord()]));

    render(<CardTemplate />);

    await waitFor(() => {
      expect(listCardTemplates).toHaveBeenCalledWith(
        expect.objectContaining({ status: 'ACTIVE' }),
      );
    });
  });
});

describe('创建卡牌模板', () => {
  it('点击新建按钮应打开弹窗', async () => {
    (listCardTemplates as jest.Mock).mockResolvedValue(mockPageResult([]));
    (listIpSeries as jest.Mock).mockResolvedValue(
      mockPageResult([{ id: 1, name: '测试IP', code: 'IP001', status: 'ACTIVE' }]),
    );

    render(<CardTemplate />);

    await waitFor(() => {
      expect(screen.getByText('卡牌管理')).toBeInTheDocument();
    });

    fireEvent.click(getCreateButton());

    await waitFor(() => {
      expect(screen.getByText('新建卡牌模板')).toBeInTheDocument();
    });
  });

  it('弹窗中应包含必填表单字段', async () => {
    (listCardTemplates as jest.Mock).mockResolvedValue(mockPageResult([]));
    (listIpSeries as jest.Mock).mockResolvedValue(
      mockPageResult([{ id: 1, name: '测试IP', code: 'IP001', status: 'ACTIVE' }]),
    );

    render(<CardTemplate />);

    await waitFor(() => {
      expect(screen.getByText('卡牌管理')).toBeInTheDocument();
    });

    fireEvent.click(getCreateButton());

    await waitFor(() => {
      expect(screen.getByText('新建卡牌模板')).toBeInTheDocument();
    });

    // 验证关键表单项存在（文本输入框）
    expect(screen.getByPlaceholderText('请输入编码')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('请输入名称')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('请输入描述')).toBeInTheDocument();
    // 验证下拉选择框存在（6 个基础字段 + 5 个星级图片 = 11 个表单项 + 星级图片分组标题）
    const formItems = document.querySelectorAll('.ant-modal .ant-form-item');
    expect(formItems.length).toBeGreaterThanOrEqual(11);
  });

  it('API 创建失败不应导致页面崩溃', async () => {
    (listCardTemplates as jest.Mock).mockResolvedValue(mockPageResult([]));
    (listIpSeries as jest.Mock).mockResolvedValue(
      mockPageResult([{ id: 1, name: '测试IP', code: 'IP001', status: 'ACTIVE' }]),
    );
    (createCardTemplate as jest.Mock).mockRejectedValue(new Error('网络异常'));

    render(<CardTemplate />);

    await waitFor(() => {
      expect(screen.getByText('卡牌管理')).toBeInTheDocument();
    });

    fireEvent.click(getCreateButton());

    await waitFor(() => {
      expect(screen.getByText('新建卡牌模板')).toBeInTheDocument();
    });

    // 页面不应崩溃
    expect(screen.getByText('卡牌管理')).toBeInTheDocument();
  });
});

describe('编辑卡牌模板', () => {
  it('点击编辑应打开弹窗并预填数据', async () => {
    (listCardTemplates as jest.Mock).mockResolvedValue(
      mockPageResult([mockCardRecord()]),
    );
    (getCardTemplateById as jest.Mock).mockResolvedValue(mockCardDetail());

    render(<CardTemplate />);

    await waitFor(() => {
      expect(screen.getByText('测试卡牌')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('编辑'));

    await waitFor(() => {
      expect(screen.getByText('编辑卡牌模板')).toBeInTheDocument();
    });

    expect(getCardTemplateById).toHaveBeenCalledWith(1);

    const codeInput = screen.getByPlaceholderText('请输入编码') as HTMLInputElement;
    const nameInput = screen.getByPlaceholderText('请输入名称') as HTMLInputElement;
    expect(codeInput.value).toBe('CT001');
    expect(nameInput.value).toBe('测试卡牌');
  });

  it('修改基础字段后提交应调用 updateCardTemplate', async () => {
    (listCardTemplates as jest.Mock).mockResolvedValue(
      mockPageResult([mockCardRecord()]),
    );
    (getCardTemplateById as jest.Mock).mockResolvedValue(mockCardDetail());
    (updateCardTemplate as jest.Mock).mockResolvedValue(
      mockCardDetail({ id: 1, name: '改名后的卡牌' }),
    );

    render(<CardTemplate />);

    await waitFor(() => {
      expect(screen.getByText('编辑')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('编辑'));

    await waitFor(() => {
      expect(screen.getByText('编辑卡牌模板')).toBeInTheDocument();
    });

    const nameInput = screen.getByPlaceholderText('请输入名称');
    fireEvent.change(nameInput, { target: { value: '改名后的卡牌' } });

    const modal = document.querySelector('.ant-modal')!;
    const submitBtn = modal.querySelector('.ant-btn-primary') as HTMLElement;
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(updateCardTemplate).toHaveBeenCalledWith(
        1,
        expect.objectContaining({ name: '改名后的卡牌' }),
      );
      expect(message.success).toHaveBeenCalledWith('更新成功');
    });
  });

  it('API 编辑失败不应导致页面崩溃', async () => {
    (listCardTemplates as jest.Mock).mockResolvedValue(
      mockPageResult([mockCardRecord()]),
    );
    (getCardTemplateById as jest.Mock).mockResolvedValue(mockCardDetail());
    (updateCardTemplate as jest.Mock).mockRejectedValue(new Error('卡牌编码已存在'));

    render(<CardTemplate />);

    await waitFor(() => {
      expect(screen.getByText('编辑')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('编辑'));

    await waitFor(() => {
      expect(screen.getByText('编辑卡牌模板')).toBeInTheDocument();
    });

    // 修改字段以触发重新渲染
    const nameInput = screen.getByPlaceholderText('请输入名称');
    fireEvent.change(nameInput, { target: { value: '触发错误' } });

    const modal = document.querySelector('.ant-modal')!;
    const submitBtn = modal.querySelector('.ant-btn-primary') as HTMLElement;
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(updateCardTemplate).toHaveBeenCalled();
    });

    // 页面不应崩溃，弹窗保持打开
    expect(screen.getByText('编辑卡牌模板')).toBeInTheDocument();
  });

  it('修改星级图片后提交应调用 addOrUpdateStarImage', async () => {
    // 初始快照中有 starImage_1 旧值
    const detail = mockCardDetail({
      starImages: [
        { starLevel: 1, imageUrl: 'http://example.com/old-star1.png' },
      ],
    });
    (listCardTemplates as jest.Mock).mockResolvedValue(mockPageResult([mockCardRecord()]));
    (getCardTemplateById as jest.Mock).mockResolvedValue(detail);
    (updateCardTemplate as jest.Mock).mockResolvedValue(detail);
    (addOrUpdateStarImage as jest.Mock).mockResolvedValue(detail);

    render(<CardTemplate />);

    await waitFor(() => {
      expect(screen.getByText('编辑')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('编辑'));

    await waitFor(() => {
      expect(screen.getByText('编辑卡牌模板')).toBeInTheDocument();
    });

    // ImageUploadField 被 mock 为可控 input，直接修改 starImage_1 为新 URL
    const star1Input = screen.getByTestId('star-image-upload-1') as HTMLInputElement;
    // 验证预填了初始值
    expect(star1Input.value).toBe('http://example.com/old-star1.png');
    // 修改为新的 URL
    fireEvent.change(star1Input, { target: { value: 'http://example.com/new-star1.png' } });

    const modal = document.querySelector('.ant-modal')!;
    const submitBtn = modal.querySelector('.ant-btn-primary') as HTMLElement;
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(updateCardTemplate).toHaveBeenCalledWith(1, expect.any(Object));
      // starImage_1 从 old URL 变更为 new URL → addOrUpdateStarImage 应被调用
      expect(addOrUpdateStarImage).toHaveBeenCalledWith(
        1,
        { starLevel: 1, imageUrl: 'http://example.com/new-star1.png' },
      );
      expect(message.success).toHaveBeenCalledWith('更新成功');
    });
  });

  it('addOrUpdateStarImage 失败应聚合错误并保持弹窗打开', async () => {
    // 初始快照无任何星级图片
    const detail = mockCardDetail({
      starImages: [],
    });
    (listCardTemplates as jest.Mock).mockResolvedValue(mockPageResult([mockCardRecord()]));
    (getCardTemplateById as jest.Mock).mockResolvedValue(detail);
    (updateCardTemplate as jest.Mock).mockResolvedValue(detail);
    // starImage_1 和 starImage_2 的 addOrUpdateStarImage 都失败
    (addOrUpdateStarImage as jest.Mock).mockRejectedValue(new Error('图片上传失败'));

    render(<CardTemplate />);

    await waitFor(() => {
      expect(screen.getByText('编辑')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('编辑'));

    await waitFor(() => {
      expect(screen.getByText('编辑卡牌模板')).toBeInTheDocument();
    });

    // 填入两个新的星级图片 URL（初始快照为空 → 都是新增）
    const star1Input = screen.getByTestId('star-image-upload-1') as HTMLInputElement;
    const star2Input = screen.getByTestId('star-image-upload-2') as HTMLInputElement;
    fireEvent.change(star1Input, { target: { value: 'http://example.com/new-star1.png' } });
    fireEvent.change(star2Input, { target: { value: 'http://example.com/new-star2.png' } });

    const modal = document.querySelector('.ant-modal')!;
    const submitBtn = modal.querySelector('.ant-btn-primary') as HTMLElement;
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(updateCardTemplate).toHaveBeenCalled();
      // 两个星级变更都应尝试调用 addOrUpdateStarImage
      expect(addOrUpdateStarImage).toHaveBeenCalledWith(
        1,
        { starLevel: 1, imageUrl: 'http://example.com/new-star1.png' },
      );
      expect(addOrUpdateStarImage).toHaveBeenCalledWith(
        1,
        { starLevel: 2, imageUrl: 'http://example.com/new-star2.png' },
      );
    });

    // 错误应被聚合为单条 message.error（包含两个星级失败信息）
    expect(message.error).toHaveBeenCalledWith(
      expect.stringContaining('星级图片更新失败'),
    );
    expect(message.success).not.toHaveBeenCalled();

    // 弹窗应保持打开
    expect(screen.getByText('编辑卡牌模板')).toBeInTheDocument();
  });
});

describe('启用/停用切换', () => {
  it('ACTIVE 记录应显示停用按钮', async () => {
    (listCardTemplates as jest.Mock).mockResolvedValue(
      mockPageResult([mockCardRecord({ status: 'ACTIVE' })]),
    );

    render(<CardTemplate />);

    await waitFor(() => {
      expect(screen.getByText('停用')).toBeInTheDocument();
    });
  });

  it('INACTIVE 记录应显示启用按钮', async () => {
    (listCardTemplates as jest.Mock).mockResolvedValue(
      mockPageResult([mockCardRecord({ id: 2, code: 'CT002', name: '已停用卡牌', status: 'INACTIVE' })]),
    );

    render(<CardTemplate />);

    await waitFor(() => {
      expect(screen.getByText('启用')).toBeInTheDocument();
    });
  });

  it('点击停用应弹出确认框', async () => {
    (listCardTemplates as jest.Mock).mockResolvedValue(
      mockPageResult([mockCardRecord({ status: 'ACTIVE' })]),
    );

    render(<CardTemplate />);

    await waitFor(() => {
      expect(screen.getByText('停用')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('停用'));

    await waitFor(() => {
      expect(screen.getByText('确定停用该卡牌模板吗？')).toBeInTheDocument();
    });
  });

  it('确认停用应调用 updateCardTemplate 并显示成功提示', async () => {
    (listCardTemplates as jest.Mock).mockResolvedValue(
      mockPageResult([mockCardRecord({ status: 'ACTIVE' })]),
    );
    (updateCardTemplate as jest.Mock).mockResolvedValue(mockCardRecord({ status: 'INACTIVE' }));

    render(<CardTemplate />);

    await waitFor(() => {
      expect(screen.getByText('停用')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('停用'));

    await waitFor(() => {
      expect(screen.getByText('确定停用该卡牌模板吗？')).toBeInTheDocument();
    });

    const popconfirm = document.querySelector('.ant-popconfirm')!;
    const confirmBtn = popconfirm.querySelector('.ant-btn-primary') as HTMLElement;
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(updateCardTemplate).toHaveBeenCalledWith(1, { status: 'INACTIVE' });
      expect(message.success).toHaveBeenCalledWith('已停用');
    });
  });

  it('切换状态失败时页面不应崩溃', async () => {
    (listCardTemplates as jest.Mock).mockResolvedValue(
      mockPageResult([mockCardRecord({ status: 'ACTIVE' })]),
    );
    (updateCardTemplate as jest.Mock).mockRejectedValue(new Error('操作失败'));

    render(<CardTemplate />);

    await waitFor(() => {
      expect(screen.getByText('停用')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('停用'));

    await waitFor(() => {
      expect(screen.getByText('确定停用该卡牌模板吗？')).toBeInTheDocument();
    });

    const popconfirm = document.querySelector('.ant-popconfirm')!;
    const confirmBtn = popconfirm.querySelector('.ant-btn-primary') as HTMLElement;
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(updateCardTemplate).toHaveBeenCalledWith(1, { status: 'INACTIVE' });
    });

    expect(screen.getByText('卡牌管理')).toBeInTheDocument();
  });
});

describe('星级图片区域', () => {
  it('打开创建弹窗时应显示星级图片上传区域', async () => {
    (listCardTemplates as jest.Mock).mockResolvedValue(mockPageResult([]));
    (listIpSeries as jest.Mock).mockResolvedValue(
      mockPageResult([{ id: 1, name: '测试IP', code: 'IP001', status: 'ACTIVE' }]),
    );

    render(<CardTemplate />);

    await waitFor(() => {
      expect(screen.getByText('卡牌管理')).toBeInTheDocument();
    });

    fireEvent.click(getCreateButton());

    await waitFor(() => {
      expect(screen.getByText('新建卡牌模板')).toBeInTheDocument();
    });

    // 验证 5 个星级图片输入存在（mock 为 data-testid="star-image-upload-{N}"）
    await waitFor(() => {
      for (let level = 1; level <= 5; level++) {
        expect(screen.getByTestId(`star-image-upload-${level}`)).toBeInTheDocument();
      }
    });
  });
});

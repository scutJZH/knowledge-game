import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

/** 模拟服务模块 */
jest.mock('@/services/cardTemplate', () => ({
  listCardTemplates: jest.fn(),
  getCardTemplateById: jest.fn(),
  createCardTemplate: jest.fn(),
  updateCardTemplate: jest.fn(),
}));

jest.mock('@/services/ipSeries', () => ({
  listIpSeries: jest.fn(),
}));

/** Mock ImageUploadField 依赖的服务 */
jest.mock('@/services/fileUpload', () => ({
  getUploadCredential: jest.fn(),
  uploadFile: jest.fn(),
}));

jest.mock('@/utils/token', () => ({
  getUserInfo: jest.fn().mockReturnValue({ id: 1 }),
}));

/** Mock ImageUploadField 为可控 input（接受 url 属性用于预填） */
jest.mock('@/components/ImageUploadField', () => ({
  __esModule: true,
  default: ({ bizType, placeholder, url, onChange }: {
    bizType: string;
    placeholder?: string;
    preview?: boolean;
    allowRemove?: boolean;
    url?: string;
    onChange?: (value: string | undefined) => void;
  }) => (
    <input
      data-testid="image-upload-field"
      type="text"
      placeholder={placeholder}
      value={url || ''}
      onChange={(e) => onChange?.(e.target.value || undefined)}
    />
  ),
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
    imageFileId: null,
    status: 'ACTIVE',
    createdAt: '2025-01-01T00:00:00',
    updatedAt: '2025-01-01T00:00:00',
    ...overrides,
  };
}

function mockCardDetail(overrides = {}) {
  return {
    ...mockCardRecord(overrides),
    imageUrl: 'http://example.com/card.png',
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

  it('弹窗中应包含基础表单字段和单个 ImageUploadField', async () => {
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

    // 验证关键表单项存在
    expect(screen.getByPlaceholderText('请输入编码')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('请输入名称')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('请输入描述')).toBeInTheDocument();

    // 验证只有一个 ImageUploadField（不是 5 个星级图片）
    const imageUpload = screen.getByTestId('image-upload-field');
    expect(imageUpload).toBeInTheDocument();
    expect(imageUpload.getAttribute('placeholder')).toBe('上传卡面图');
  });

  it('弹窗中不应包含 starImage 相关字段', async () => {
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

    // 不应存在星级图片相关的 testid
    const starInputs = screen.queryAllByTestId(/star-image-upload/);
    expect(starInputs.length).toBe(0);
  });

  it('表单提交应调用 createCardTemplate 并包含 imageFileId', async () => {
    (listCardTemplates as jest.Mock).mockResolvedValue(mockPageResult([]));
    (listIpSeries as jest.Mock).mockResolvedValue(
      mockPageResult([{ id: 1, name: '测试IP', code: 'IP001', status: 'ACTIVE' }]),
    );
    (createCardTemplate as jest.Mock).mockResolvedValue(
      mockCardDetail({ id: 1, name: '新卡牌' }),
    );

    render(<CardTemplate />);

    await waitFor(() => {
      expect(screen.getByText('卡牌管理')).toBeInTheDocument();
    });

    fireEvent.click(getCreateButton());

    await waitFor(() => {
      expect(screen.getByText('新建卡牌模板')).toBeInTheDocument();
    });

    // 填写基础文本字段
    const codeInput = screen.getByPlaceholderText('请输入编码');
    fireEvent.change(codeInput, { target: { value: 'NEW001' } });

    const nameInput = screen.getByPlaceholderText('请输入名称');
    fireEvent.change(nameInput, { target: { value: '新卡牌' } });

    // 填写 imageFileId（mock 接受任意字符串值，最终会被透传为 imageFileId）
    const imageInput = screen.getByTestId('image-upload-field');
    fireEvent.change(imageInput, { target: { value: 'http://example.com/new-card.png' } });

    // 选择 IP 系列（第一个 Select 选择器）
    const selectSelectors = document.querySelectorAll('.ant-modal .ant-select-selector');
    if (selectSelectors.length > 0) {
      fireEvent.mouseDown(selectSelectors[0]);
      // 等待下拉选项出现并点击第一个
      await waitFor(() => {
        const firstOption = document.querySelector('.ant-select-item-option-content');
        if (firstOption) fireEvent.click(firstOption);
      });
    }

    // 选择稀有度（第三个 Select，IP系列、稀有度、状态）
    const raritySelector = document.querySelectorAll('.ant-modal .ant-select-selector')[1];
    if (raritySelector) {
      fireEvent.mouseDown(raritySelector);
      await waitFor(() => {
        const options = document.querySelectorAll('.ant-select-item-option-content');
        // SR 是第三个稀有度选项
        if (options.length > 2) fireEvent.click(options[2]);
      });
    }

    // 提交表单
    const modal = document.querySelector('.ant-modal')!;
    const submitBtn = modal.querySelector('.ant-btn-primary') as HTMLElement;
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(createCardTemplate).toHaveBeenCalledWith(
        expect.objectContaining({
          imageFileId: 'http://example.com/new-card.png',
        }),
      );
      expect(message.success).toHaveBeenCalledWith('创建成功');
    });
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
  it('点击编辑应打开弹窗并预填数据（含 imageUrl）', async () => {
    (listCardTemplates as jest.Mock).mockResolvedValue(
      mockPageResult([mockCardRecord()]),
    );
    (getCardTemplateById as jest.Mock).mockResolvedValue(
      mockCardDetail({ imageUrl: 'http://example.com/card.png' }),
    );

    render(<CardTemplate />);

    await waitFor(() => {
      expect(screen.getByText('测试卡牌')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('编辑'));

    await waitFor(() => {
      expect(screen.getByText('编辑卡牌模板')).toBeInTheDocument();
    });

    expect(getCardTemplateById).toHaveBeenCalledWith(1);

    // 基础字段预填
    const codeInput = screen.getByPlaceholderText('请输入编码') as HTMLInputElement;
    const nameInput = screen.getByPlaceholderText('请输入名称') as HTMLInputElement;
    expect(codeInput.value).toBe('CT001');
    expect(nameInput.value).toBe('测试卡牌');

    // imageUrl 预填
    const imageInput = screen.getByTestId('image-upload-field') as HTMLInputElement;
    expect(imageInput.value).toBe('http://example.com/card.png');
  });

  it('修改基础字段后提交应调用 updateCardTemplate', async () => {
    (listCardTemplates as jest.Mock).mockResolvedValue(
      mockPageResult([mockCardRecord()]),
    );
    (getCardTemplateById as jest.Mock).mockResolvedValue(
      mockCardDetail({ imageUrl: 'http://example.com/card.png' }),
    );
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
        expect.objectContaining({
          name: '改名后的卡牌',
        }),
      );
      expect(message.success).toHaveBeenCalledWith('更新成功');
    });
  });

  /**
   * 三态场景：未变更字段不出现在 update payload
   */
  it('未变更字段不出现在 update payload', async () => {
    (listCardTemplates as jest.Mock).mockResolvedValue(
      mockPageResult([mockCardRecord()]),
    );
    (getCardTemplateById as jest.Mock).mockResolvedValue(mockCardDetail());
    (updateCardTemplate as jest.Mock).mockResolvedValue(mockCardDetail());

    render(<CardTemplate />);

    await waitFor(() => {
      expect(screen.getByText('编辑')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('编辑'));

    await waitFor(() => {
      expect(screen.getByText('编辑卡牌模板')).toBeInTheDocument();
    });

    // 不修改任何字段，直接提交
    const modal = document.querySelector('.ant-modal')!;
    const submitBtn = modal.querySelector('.ant-btn-primary') as HTMLElement;
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(updateCardTemplate).toHaveBeenCalledWith(1, {});
    });
  });

  /**
   * 三态场景：清空 description（用户将描述设为空字符串）→ payload.description = null
   */
  it('清空 description 时 payload.description = null', async () => {
    (listCardTemplates as jest.Mock).mockResolvedValue(
      mockPageResult([mockCardRecord()]),
    );
    (getCardTemplateById as jest.Mock).mockResolvedValue(
      mockCardDetail({ description: '原描述' }),
    );
    (updateCardTemplate as jest.Mock).mockResolvedValue(mockCardDetail());

    render(<CardTemplate />);

    await waitFor(() => {
      expect(screen.getByText('编辑')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('编辑'));

    await waitFor(() => {
      expect(screen.getByText('编辑卡牌模板')).toBeInTheDocument();
    });

    const descInput = screen.getByPlaceholderText('请输入描述');
    fireEvent.change(descInput, { target: { value: '' } });

    const modal = document.querySelector('.ant-modal')!;
    const submitBtn = modal.querySelector('.ant-btn-primary') as HTMLElement;
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(updateCardTemplate).toHaveBeenCalledWith(
        1,
        expect.objectContaining({ description: null }),
      );
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

describe('ImageUploadField 区域', () => {
  it('打开创建弹窗时应显示单个 ImageUploadField（非 5 个星级图片）', async () => {
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

    // 只有一个 image-upload-field
    const imageUploads = screen.getAllByTestId('image-upload-field');
    expect(imageUploads.length).toBe(1);
  });

  it('打开编辑弹窗时 imageUrl 预填值应来自详情接口', async () => {
    (listCardTemplates as jest.Mock).mockResolvedValue(
      mockPageResult([mockCardRecord()]),
    );
    (getCardTemplateById as jest.Mock).mockResolvedValue(
      mockCardDetail({ imageUrl: 'http://example.com/specific-card.png' }),
    );

    render(<CardTemplate />);

    await waitFor(() => {
      expect(screen.getByText('编辑')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('编辑'));

    await waitFor(() => {
      const imageInput = screen.getByTestId('image-upload-field') as HTMLInputElement;
      expect(imageInput.value).toBe('http://example.com/specific-card.png');
    });
  });

  it('详情接口无 imageUrl 时应为空', async () => {
    (listCardTemplates as jest.Mock).mockResolvedValue(
      mockPageResult([mockCardRecord()]),
    );
    (getCardTemplateById as jest.Mock).mockResolvedValue(
      mockCardDetail({ imageUrl: undefined }),
    );

    render(<CardTemplate />);

    await waitFor(() => {
      expect(screen.getByText('编辑')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('编辑'));

    await waitFor(() => {
      const imageInput = screen.getByTestId('image-upload-field') as HTMLInputElement;
      expect(imageInput.value).toBe('');
    });
  });
});

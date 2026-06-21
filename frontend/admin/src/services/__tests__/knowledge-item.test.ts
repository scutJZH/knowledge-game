import {
  downloadImportTemplate,
  importExcel,
  importMarkdownZip,
} from '../knowledge-item';

/** 模拟 umi 的 request 函数 */
jest.mock('umi', () => ({
  request: jest.fn(),
}));

const { request } = require('umi');

beforeEach(() => {
  jest.clearAllMocks();
});

describe('downloadImportTemplate', () => {
  it('应该以 GET 方法调用模板下载，responseType 为 blob', async () => {
    const mockBlob = new Blob(['test']);
    request.mockResolvedValue(mockBlob);

    const result = await downloadImportTemplate();

    expect(request).toHaveBeenCalledWith('/api/admin/knowledge-items/import-template', {
      method: 'GET',
      responseType: 'blob',
    });
    expect(result).toEqual(mockBlob);
  });
});

describe('importExcel', () => {
  it('应该以 POST 方法调用，FormData 包含 file', async () => {
    const mockResult = { totalCount: 1, successCount: 1, failCount: 0, failDetails: [] };
    request.mockResolvedValue(mockResult);

    const file = new File(['fake'], 'test.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });
    const result = await importExcel(file);

    expect(request).toHaveBeenCalledWith('/api/admin/knowledge-items/import', {
      method: 'POST',
      data: expect.any(FormData),
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    const callArgs = request.mock.calls[0];
    const formData = callArgs[1].data as FormData;
    expect(formData.get('file')).toEqual(file);
    expect(result).toEqual(mockResult);
  });
});

describe('importMarkdownZip', () => {
  it('应该以 POST 方法调用，FormData 包含 file', async () => {
    const mockResult = { totalCount: 2, successCount: 2, failCount: 0, failDetails: [] };
    request.mockResolvedValue(mockResult);

    const file = new File(['fake'], 'test.zip', { type: 'application/zip' });
    const result = await importMarkdownZip(file);

    expect(request).toHaveBeenCalledWith('/api/admin/knowledge-items/import-markdown', {
      method: 'POST',
      data: expect.any(FormData),
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    const callArgs = request.mock.calls[0];
    const formData = callArgs[1].data as FormData;
    expect(formData.get('file')).toEqual(file);
    expect(result).toEqual(mockResult);
  });
});

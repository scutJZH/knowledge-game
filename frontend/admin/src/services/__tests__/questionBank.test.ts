// mock umi request 必须在所有 import 之前
jest.mock('umi', () => ({
  request: jest.fn(),
}));

import { request } from 'umi';
import {
  listQuestions,
  getQuestionById,
  createQuestion,
  updateQuestion,
  updateQuestionCategories,
  batchActivate,
  batchDeactivate,
  downloadImportTemplate,
  importQuestions,
  DIFFICULTY_OPTIONS,
  QUESTION_TYPE_OPTIONS,
  QUESTION_STATUS_OPTIONS,
} from '../questionBank';

const mockRequest = jest.mocked(request);

describe('questionBank API', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('listQuestions', () => {
    it('应使用正确的 GET URL 和 query params', async () => {
      const params = {
        keyword: 'test',
        type: 'SINGLE_CHOICE' as const,
        difficulty: 1 as const,
        categoryId: 5,
        status: 'ACTIVE' as const,
        sort: 'updatedAt' as const,
        order: 'desc' as const,
        page: 0,
        size: 20,
      };
      await listQuestions(params);
      expect(mockRequest).toHaveBeenCalledWith('/api/admin/questions', {
        method: 'GET',
        params,
      });
    });

    it('空 params 也应正确调用', async () => {
      await listQuestions({});
      expect(mockRequest).toHaveBeenCalledWith('/api/admin/questions', {
        method: 'GET',
        params: {},
      });
    });
  });

  describe('getQuestionById', () => {
    it('应使用正确的 GET URL', async () => {
      await getQuestionById(42);
      expect(mockRequest).toHaveBeenCalledWith('/api/admin/questions/42', {
        method: 'GET',
      });
    });
  });

  describe('createQuestion', () => {
    it('应使用正确的 POST URL 和 body', async () => {
      const data = {
        type: 'SINGLE_CHOICE' as const,
        content: 'What is Java?',
        options: [
          { key: 'A', content: 'A language' },
          { key: 'B', content: 'A coffee' },
        ],
        answer: 'A',
        difficulty: 1 as const,
        explanation: 'Java is a programming language',
        tags: ['basic'],
        categoryIds: [1, 2],
      };
      await createQuestion(data);
      expect(mockRequest).toHaveBeenCalledWith('/api/admin/questions', {
        method: 'POST',
        data,
      });
    });
  });

  describe('updateQuestion', () => {
    it('应使用正确的 PUT URL 和 body', async () => {
      const data = {
        content: 'Updated content',
        difficulty: 2 as const,
      };
      await updateQuestion(42, data);
      expect(mockRequest).toHaveBeenCalledWith('/api/admin/questions/42', {
        method: 'PUT',
        data,
      });
    });
  });

  describe('updateQuestionCategories', () => {
    it('应使用正确的 PUT URL 和 body', async () => {
      await updateQuestionCategories(42, [1, 2, 3]);
      expect(mockRequest).toHaveBeenCalledWith(
        '/api/admin/questions/42/categories',
        {
          method: 'PUT',
          data: { categoryIds: [1, 2, 3] },
        },
      );
    });
  });

  describe('batchActivate', () => {
    it('应使用正确的 PUT URL 和 body', async () => {
      await batchActivate([1, 2, 3]);
      expect(mockRequest).toHaveBeenCalledWith(
        '/api/admin/questions/batch-activate',
        {
          method: 'PUT',
          data: { ids: [1, 2, 3] },
        },
      );
    });
  });

  describe('batchDeactivate', () => {
    it('应使用正确的 PUT URL 和 body', async () => {
      await batchDeactivate([5, 6]);
      expect(mockRequest).toHaveBeenCalledWith(
        '/api/admin/questions/batch-deactivate',
        {
          method: 'PUT',
          data: { ids: [5, 6] },
        },
      );
    });
  });

  describe('downloadImportTemplate', () => {
    it('应使用 GET + responseType: blob', async () => {
      await downloadImportTemplate();
      expect(mockRequest).toHaveBeenCalledWith(
        '/api/admin/questions/import-template',
        {
          method: 'GET',
          responseType: 'blob',
        },
      );
    });
  });

  describe('importQuestions', () => {
    it('应使用 POST + FormData（字段名为 file）', async () => {
      const file = new File(['test'], 'test.xlsx', {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      });
      await importQuestions(file);
      expect(mockRequest).toHaveBeenCalledWith(
        '/api/admin/questions/import',
        expect.objectContaining({
          method: 'POST',
          data: expect.any(FormData),
        }),
      );
      const callData = (mockRequest.mock.calls[0] as any)[1];
      expect(callData.data.get('file')).toBe(file);
    });
  });
});

describe('常量映射', () => {
  it('DIFFICULTY_OPTIONS 应包含 3 个条目', () => {
    expect(DIFFICULTY_OPTIONS).toHaveLength(3);
    expect(DIFFICULTY_OPTIONS[0]).toEqual({ value: 1, label: '简单', color: 'green' });
    expect(DIFFICULTY_OPTIONS[2]).toEqual({ value: 3, label: '困难', color: 'red' });
  });

  it('QUESTION_TYPE_OPTIONS 应包含 4 个条目', () => {
    expect(QUESTION_TYPE_OPTIONS).toHaveLength(4);
    const values = QUESTION_TYPE_OPTIONS.map((o) => o.value);
    expect(values).toContain('SINGLE_CHOICE');
    expect(values).toContain('MULTIPLE_CHOICE');
    expect(values).toContain('TRUE_FALSE');
    expect(values).toContain('FILL_BLANK');
  });

  it('QUESTION_STATUS_OPTIONS 应包含 ACTIVE 和 INACTIVE', () => {
    expect(QUESTION_STATUS_OPTIONS).toHaveLength(2);
    const values = QUESTION_STATUS_OPTIONS.map((o) => o.value);
    expect(values).toEqual(['ACTIVE', 'INACTIVE']);
  });
});

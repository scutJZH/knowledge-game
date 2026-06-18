import { render } from '@testing-library/react';
import KnowledgeItemPage from '../index';

// Mock services
jest.mock('@/services/knowledge-item', () => ({
  listKnowledgeItems: jest.fn().mockResolvedValue({ content: [], totalElements: 0 }),
  getKnowledgeItemById: jest.fn().mockResolvedValue({}),
  createKnowledgeItem: jest.fn().mockResolvedValue({}),
  updateKnowledgeItem: jest.fn().mockResolvedValue({}),
  deleteKnowledgeItem: jest.fn().mockResolvedValue({}),
  getKnowledgeItemCategories: jest.fn().mockResolvedValue([]),
  updateKnowledgeItemCategories: jest.fn().mockResolvedValue({}),
  batchActivate: jest.fn().mockResolvedValue({}),
  batchDeactivate: jest.fn().mockResolvedValue({}),
  batchSort: jest.fn().mockResolvedValue({}),
}));

jest.mock('@/services/knowledge-category', () => ({
  getTree: jest.fn().mockResolvedValue([]),
  convertToTreeDataActiveOnly: jest.fn(() => []),
}));

jest.mock('@/components/VditorEditor', () => ({
  __esModule: true,
  default: () => <div data-testid="vditor-mock" />,
}));

describe('KnowledgeItemPage', () => {
  it('renders without crashing', () => {
    const { container } = render(<KnowledgeItemPage />);
    expect(container).toBeTruthy();
  });
});

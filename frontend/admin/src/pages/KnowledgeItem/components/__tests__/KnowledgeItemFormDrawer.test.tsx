import { render, screen } from '@testing-library/react';
import KnowledgeItemFormDrawer from '../KnowledgeItemFormDrawer';

// Mock the VditorEditor to avoid loading vditor
jest.mock('@/components/VditorEditor', () => ({
  __esModule: true,
  default: ({ value, onChange }: any) => (
    <textarea data-testid="vditor-mock" value={value} onChange={(e) => onChange?.(e.target.value)} />
  ),
}));

// Mock knowledge-category service
jest.mock('@/services/knowledge-category', () => ({
  getTree: jest.fn().mockResolvedValue([]),
  convertToTreeDataActiveOnly: jest.fn(() => []),
}));

// Mock knowledge-item service
jest.mock('@/services/knowledge-item', () => ({
  createKnowledgeItem: jest.fn().mockResolvedValue({}),
  updateKnowledgeItem: jest.fn().mockResolvedValue({}),
  updateKnowledgeItemCategories: jest.fn().mockResolvedValue({}),
  getKnowledgeItemCategories: jest.fn().mockResolvedValue([]),
}));

describe('KnowledgeItemFormDrawer', () => {
  const defaultProps = {
    open: true,
    mode: 'create' as const,
    initialValues: {},
    onClose: jest.fn(),
    onSubmit: jest.fn(),
  };

  it('renders create form', () => {
    render(<KnowledgeItemFormDrawer {...defaultProps} />);
    // Form renders with title "新建知识条目"
    expect(screen.getByText('新建知识条目')).toBeInTheDocument();
  });
});

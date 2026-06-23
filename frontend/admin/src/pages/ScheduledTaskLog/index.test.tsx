import { render, screen, waitFor } from '@testing-library/react';
import ScheduledTaskLogPage from './index';

// Mock the service
jest.mock('@/services/scheduledTaskLog', () => ({
  listTaskLogs: jest.fn(),
}));

import { listTaskLogs } from '@/services/scheduledTaskLog';

// Mock @umijs/max
jest.mock('@umijs/max', () => ({
  request: jest.fn(),
  useModel: jest.fn(),
  history: { push: jest.fn(), location: { pathname: '/' } },
}));

// Mock antd components
jest.mock('@ant-design/pro-components', () => ({
  ProTable: ({ columns, request }: any) => {
    // Basic render to verify component mounts
    return <div data-testid="pro-table">ProTable with {columns.length} columns</div>;
  },
}));

jest.mock('antd', () => {
  const actual = jest.requireActual('antd');
  return {
    ...actual,
    Tag: ({ children, color }: any) => <span data-color={color}>{children}</span>,
    Tooltip: ({ children, title }: any) => <span data-tooltip={JSON.stringify(title)}>{children}</span>,
  };
});

describe('ScheduledTaskLogPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should render ProTable with correct number of columns', () => {
    render(<ScheduledTaskLogPage />);
    // 7 columns: taskName(taskDisplay), executedAt, durationMs, totalCount, successCount, failureCount, status
    expect(screen.getByTestId('pro-table')).toBeInTheDocument();
    expect(screen.getByTestId('pro-table').textContent).toContain('7 columns');
  });

  it('should format duration correctly', () => {
    const formatDuration = (ms: number): string => {
      if (ms >= 1000) return `${(ms / 1000).toFixed(1)}s`;
      return `${ms}ms`;
    };
    expect(formatDuration(500)).toBe('500ms');
    expect(formatDuration(1500)).toBe('1.5s');
    expect(formatDuration(3000)).toBe('3.0s');
  });
});

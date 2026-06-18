import { render } from '@testing-library/react';

// Mock vditor to avoid loading the ~2MB chunk in tests
jest.mock('vditor', () => {
  return jest.fn().mockImplementation(() => ({
    destroy: jest.fn(),
    setValue: jest.fn(),
  }));
});

import VditorEditor from '../index';

describe('VditorEditor', () => {
  it('renders container div', () => {
    const { container } = render(<VditorEditor />);
    expect(container.querySelector('div')).toBeInTheDocument();
  });
});

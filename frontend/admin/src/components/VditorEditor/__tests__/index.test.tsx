import { fireEvent, render } from '@testing-library/react';

// Mock vditor to avoid loading the ~2MB chunk in tests
jest.mock('vditor', () => ({
  preview: jest.fn(),
}));

import VditorEditor from '../index';

describe('VditorEditor', () => {
  it('renders textarea', () => {
    const { container } = render(<VditorEditor />);
    expect(container.querySelector('textarea')).toBeInTheDocument();
  });

  it('renders preview button', () => {
    const { getByText } = render(<VditorEditor />);
    expect(getByText('预览')).toBeInTheDocument();
  });

  it('calls onChange when typing', () => {
    const onChange = jest.fn();
    const { container } = render(<VditorEditor value="" onChange={onChange} />);
    const textarea = container.querySelector('textarea')!;
    fireEvent.change(textarea, { target: { value: 'test' } });
    expect(onChange).toHaveBeenCalledWith('test');
  });
});

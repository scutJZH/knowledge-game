import '@testing-library/jest-dom';

/** jsdom 缺少 window.matchMedia，Ant Design 响应式组件依赖它 */
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: jest.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: jest.fn(),
    removeListener: jest.fn(),
    addEventListener: jest.fn(),
    removeEventListener: jest.fn(),
    dispatchEvent: jest.fn(),
  })),
});

/** jsdom 缺少 window.getComputedStyle，Ant Design Modal/Drawer 依赖它 */
const originalGetComputedStyle = window.getComputedStyle;
window.getComputedStyle = (elt: Element, pseudoElt?: string | null) => {
  try {
    return originalGetComputedStyle(elt, pseudoElt);
  } catch {
    return {
      getPropertyValue: () => '',
    } as unknown as CSSStyleDeclaration;
  }
};

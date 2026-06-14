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

/** jsdom 的 getComputedStyle 在读取伪元素或不存在样式时抛错，Ant Design Modal/Drawer 受影响，捕获异常时返回空 CSSStyleDeclaration */
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

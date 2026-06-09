import requestConfig from './services/request';

// 统一请求配置导出（UmiJS request 插件自动读取）
export const request = requestConfig;

// 全局布局配置
export const layout = () => {
  return {
    title: 'Knowledge Game',
    logo: false,
  };
};

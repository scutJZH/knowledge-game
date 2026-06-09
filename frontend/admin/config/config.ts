import { defineConfig } from '@umijs/max';
import defaultSettings from './defaultSettings';
import routes from './routes';
import proxy from './proxy';

export default defineConfig({
  title: defaultSettings.title,
  routes,
  proxy: proxy.dev,
  npmClient: 'npm',
  antd: {},
  model: {},
  initialState: {},
  request: {},
  layout: {
    title: defaultSettings.title,
    locale: false,
  },
});

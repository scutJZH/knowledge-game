import { ProLayoutProps } from '@ant-design/pro-components';

const Settings: ProLayoutProps & { pwa?: boolean } = {
  navTheme: 'light',
  colorPrimary: '#1890ff',
  layout: 'side',
  contentWidth: 'Fluid',
  fixedHeader: false,
  fixSiderbar: true,
  title: 'Knowledge Game',
  pwa: false,
  splitMenus: false,
};

export default Settings;

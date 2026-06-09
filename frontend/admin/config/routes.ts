export default [
  { path: '/', redirect: '/dashboard' },
  {
    path: '/dashboard',
    name: '仪表盘',
    icon: 'DashboardOutlined',
    component: './Dashboard',
  },
  { path: '*', component: './404' },
];

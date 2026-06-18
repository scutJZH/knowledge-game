export default [
  {
    path: '/login',
    layout: false,
    component: './Login',
  },
  {
    path: '/',
    redirect: '/dashboard',
  },
  {
    path: '/dashboard',
    name: '仪表盘',
    icon: 'DashboardOutlined',
    component: './Dashboard',
  },
  {
    path: '/content',
    name: '内容管理',
    icon: 'FileTextOutlined',
    routes: [
      {
        path: '/content/ip-series',
        name: 'IP 系列管理',
        component: './IpSeries',
      },
      {
        path: '/content/card-template',
        name: '卡牌管理',
        component: './CardTemplate',
      },
      {
        path: '/content/question-bank',
        name: '题库管理',
        component: './QuestionBank',
      },
      {
        path: '/content/category',
        name: '分类管理',
        component: './Category',
      },
    ],
  },
  {
    path: '/operation',
    name: '运营管理',
    icon: 'ShopOutlined',
    routes: [
      {
        path: '/operation/product',
        name: '商品管理',
        component: './Product',
      },
      {
        path: '/operation/order',
        name: '订单管理',
        component: './Order',
      },
      {
        path: '/operation/blind-box',
        name: '抽卡配置',
        component: './BlindBox',
      },
      {
        path: '/operation/achievement',
        name: '成就模板',
        component: './Achievement',
      },
    ],
  },
  {
    path: '/system',
    name: '系统',
    icon: 'SettingOutlined',
    routes: [
      {
        path: '/system/user',
        name: '用户管理',
        component: './User',
      },
    ],
  },
  {
    path: '*',
    component: './404',
  },
];

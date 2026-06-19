import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu } from 'antd';
import { UserOutlined } from '@ant-design/icons';
import './MainLayout.css';

const { Header, Content } = Layout;

const menuItems = [
  { key: '/home', label: '首页' },
  { key: '/collection', label: '图鉴' },
  { key: '/card-bag', label: '卡包' },
  { key: '/profile', label: '我的' },
];

function MainLayout() {
  const navigate = useNavigate();
  const location = useLocation();

  const selectedKey = menuItems.find((item) => item.key === location.pathname)?.key || '/home';

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 24 }}>
          <div style={{ color: '#fff', fontSize: 18, fontWeight: 'bold', whiteSpace: 'nowrap' }}>
            🃏 Knowledge Game
          </div>
          <Menu
            mode="horizontal"
            selectedKeys={[selectedKey]}
            items={menuItems}
            onClick={({ key }) => navigate(key)}
            style={{ flex: 1, minWidth: 0 }}
          />
        </div>
        <div
          style={{ color: 'rgba(255,255,255,0.65)', display: 'flex', alignItems: 'center', gap: 8 }}
        >
          <UserOutlined />
          <span>未登录</span>
        </div>
      </Header>
      <Content>
        <div className="content">
          <Outlet />
        </div>
      </Content>
    </Layout>
  );
}

export default MainLayout;

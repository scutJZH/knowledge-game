import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Avatar, Dropdown } from 'antd';
import { UserOutlined } from '@ant-design/icons';
import { useAuthStore } from '@/store/auth-store';
import { logoutApi } from '@/services/auth-api';
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
  const user = useAuthStore((state) => state.user);

  const selectedKey = menuItems.find((item) => item.key === location.pathname)?.key || '/home';

  async function handleLogout() {
    try {
      const refreshToken = useAuthStore.getState().refreshToken;
      await logoutApi(refreshToken);
    } finally {
      useAuthStore.getState().logout();
      navigate('/login');
    }
  }

  const userDropdownItems = {
    items: [
      { key: 'profile', label: '个人中心' },
      { key: 'logout', label: '退出登录', danger: true },
    ],
    onClick: ({ key }: { key: string }) => {
      if (key === 'logout') {
        handleLogout();
      } else if (key === 'profile') {
        navigate('/profile');
      }
    },
  };

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
        {user ? (
          <Dropdown menu={userDropdownItems} placement="bottomRight">
            <div style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>
              <Avatar
                size="small"
                src={user.avatarUrl}
                icon={!user.avatarUrl ? <UserOutlined /> : undefined}
              />
              <span style={{ color: 'rgba(255,255,255,0.85)' }}>{user.nickname}</span>
            </div>
          </Dropdown>
        ) : (
          <div
            style={{ color: 'rgba(255,255,255,0.65)', display: 'flex', alignItems: 'center', gap: 8 }}
          >
            <UserOutlined />
            <span>未登录</span>
          </div>
        )}
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

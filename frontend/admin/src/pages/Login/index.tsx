// frontend/admin/src/pages/Login/index.tsx
import { LoginForm, ProFormText } from '@ant-design/pro-components';
import { message } from 'antd';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
// @ts-ignore — UmiJS 构建时生成类型，IDE 无法解析虚拟路径
import { request, useModel } from '@umijs/max';
import { setAuth } from '@/utils/token';

const Login: React.FC = () => {
  const { refresh } = useModel('@@initialState');

  const handleSubmit = async (values: {
    username: string;
    password: string;
  }) => {
    try {
      // 全局拦截器已解包 Result<T>，返回值即 data 部分
      const data: any = await request('/api/admin/login', {
        method: 'POST',
        data: values,
      });
      // data = { accessToken, refreshToken, expiresIn, user: { id, username, nickname, role } }
      setAuth(data.accessToken, data.refreshToken, data.user);
      message.success('登录成功');
      // 刷新全局状态，使 getInitialState 重新读取用户信息
      await refresh();
      window.location.href = '/';
    } catch {
      // 错误已由全局拦截器展示（message.error）
    }
  };

  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100vh',
      }}
    >
      <LoginForm
        title="Knowledge Game"
        subTitle="系统管理后台"
        onFinish={handleSubmit}
      >
        <ProFormText
          name="username"
          fieldProps={{ prefix: <UserOutlined /> }}
          placeholder="用户名"
          rules={[{ required: true, message: '请输入用户名' }]}
        />
        <ProFormText.Password
          name="password"
          fieldProps={{ prefix: <LockOutlined /> }}
          placeholder="密码"
          rules={[{ required: true, message: '请输入密码' }]}
        />
      </LoginForm>
    </div>
  );
};

export default Login;

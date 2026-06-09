import { LoginForm, ProFormText } from '@ant-design/pro-components';
import { message } from 'antd';
import { LockOutlined, UserOutlined } from '@ant-design/icons';

const Login: React.FC = () => {
  // 占位：实际登录逻辑由 REQ-41 实现
  const handleSubmit = async () => {
    message.info('登录功能尚未实现');
  };

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
      <LoginForm
        title="Knowledge Game"
        subTitle="系统管理后台"
        onFinish={handleSubmit}
      >
        <ProFormText
          name="username"
          fieldProps={{ prefix: <UserOutlined /> }}
          placeholder="用户名"
          disabled
        />
        <ProFormText.Password
          name="password"
          fieldProps={{ prefix: <LockOutlined /> }}
          placeholder="密码"
          disabled
        />
      </LoginForm>
    </div>
  );
};

export default Login;

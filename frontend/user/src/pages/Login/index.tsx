import { useState } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { Form, Input, Button, Alert, Checkbox, Typography, message } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { loginApi } from '@/services/auth-api';
import { useAuthStore } from '@/store/auth-store';
import { ApiError } from '@/types/api';

const { Title } = Typography;

function Login() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [rememberMe, setRememberMe] = useState(true);
  const authLogin = useAuthStore((s) => s.login);

  async function handleSubmit(values: { username: string; password: string }) {
    setSubmitting(true);
    setError(null);
    try {
      const res = await loginApi(values);
      authLogin(res.accessToken, res.refreshToken, res.expiresIn, res.user, rememberMe);
      const rawRedirect = searchParams.get('redirect');
      const target = rawRedirect && rawRedirect.startsWith('/') && !rawRedirect.startsWith('//')
        ? rawRedirect
        : '/home';
      navigate(target, { replace: true });
    } catch (e) {
      if (e instanceof ApiError) {
        if (e.code === 400 && e.message === '用户名或密码错误') {
          setError('用户名或密码错误');
        } else if (e.httpStatus === 0) {
          setError('网络异常，请检查网络连接');
        } else {
          setError(e.message);
        }
        message.error('登录失败');
      } else {
        setError('未知错误，请稍后重试');
        message.error('登录失败');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Form onFinish={handleSubmit} size="large">
      <Title level={3} style={{ textAlign: 'center', marginBottom: 24 }}>欢迎回来</Title>
      {error && <Alert type="error" message={error} style={{ marginBottom: 16 }} closable onClose={() => setError(null)} />}
      <Form.Item name="username" rules={[
        { required: true, message: '请输入用户名' },
        { min: 2, message: '用户名长度需 2-50 字符' },
        { max: 50, message: '用户名长度需 2-50 字符' },
      ]}>
        <Input prefix={<UserOutlined />} placeholder="2-50 字符" />
      </Form.Item>
      <Form.Item name="password" rules={[
        { required: true, message: '请输入密码' },
        { min: 6, message: '密码长度 6-50 字符' },
        { max: 50, message: '密码长度 6-50 字符' },
      ]}>
        <Input.Password prefix={<LockOutlined />} placeholder="6-50 字符" />
      </Form.Item>
      <Form.Item>
        <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%' }}>
          <Checkbox checked={rememberMe} onChange={(e) => setRememberMe(e.target.checked)}>记住我</Checkbox>
          <Link to="/forgot-password">忘记密码？</Link>
        </div>
      </Form.Item>
      <Form.Item>
        <Button type="primary" htmlType="submit" loading={submitting} block>登录</Button>
      </Form.Item>
      <div style={{ textAlign: 'center' }}>
        没有账号？<Link to="/register">去注册 →</Link>
      </div>
    </Form>
  );
}

export default Login;

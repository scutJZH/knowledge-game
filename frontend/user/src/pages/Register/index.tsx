import { useState } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { Form, Input, Button, Alert, Typography, message, Modal } from 'antd';
import { UserOutlined, SmileOutlined, LockOutlined } from '@ant-design/icons';
import { registerApi, loginApi } from '@/services/auth-api';
import { useAuthStore } from '@/store/auth-store';
import { ApiError } from '@/types/api';

const { Title } = Typography;

function Register() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const authLogin = useAuthStore((s) => s.login);

  async function handleSubmit(values: { username: string; nickname: string; password: string }) {
    setSubmitting(true);
    setError(null);
    try {
      await registerApi(values);
      try {
        const loginRes = await loginApi({ username: values.username, password: values.password });
        authLogin(loginRes.accessToken, loginRes.refreshToken, loginRes.expiresIn, loginRes.user);
        message.success('注册成功，欢迎加入 Knowledge Game');
        const rawRedirect = searchParams.get('redirect');
        const target = rawRedirect && rawRedirect.startsWith('/') && !rawRedirect.startsWith('//')
          ? rawRedirect
          : '/home';
        navigate(target, { replace: true });
      } catch {
        Modal.error({
          title: '自动登录失败',
          content: '账号已创建，但自动登录失败，请手动登录',
          onOk: () => navigate('/login', { replace: true }),
        });
      }
    } catch (e) {
      if (e instanceof ApiError) {
        if (e.message.includes('用户名已存在')) {
          form.setFields([{ name: 'username', errors: ['该用户名已被注册'] }]);
        } else if (e.httpStatus === 0) {
          setError('网络异常，请检查网络连接');
        } else {
          setError(e.message);
        }
        message.error('注册失败');
      } else {
        setError('未知错误，请稍后重试');
        message.error('注册失败');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Form form={form} onFinish={handleSubmit} size="large">
      <Title level={3} style={{ textAlign: 'center', marginBottom: 24 }}>创建账号</Title>
      {error && <Alert type="error" message={error} style={{ marginBottom: 16 }} closable onClose={() => setError(null)} />}
      <Form.Item name="username" rules={[
        { required: true, message: '请输入用户名' },
        { min: 2, message: '用户名长度需 2-50 字符' },
        { max: 50, message: '用户名长度需 2-50 字符' },
      ]}>
        <Input prefix={<UserOutlined />} placeholder="2-50 字符" />
      </Form.Item>
      <Form.Item name="nickname" rules={[
        { required: true, message: '请输入昵称' },
        { min: 1, message: '昵称长度 1-50 字符' },
        { max: 50, message: '昵称长度 1-50 字符' },
      ]}>
        <Input prefix={<SmileOutlined />} placeholder="你的昵称" />
      </Form.Item>
      <Form.Item name="password" rules={[
        { required: true, message: '请输入密码' },
        { min: 6, message: '密码长度 6-50 字符' },
        { max: 50, message: '密码长度 6-50 字符' },
      ]}>
        <Input.Password prefix={<LockOutlined />} placeholder="6-50 字符" />
      </Form.Item>
      <Form.Item
        name="confirmPassword"
        dependencies={['password']}
        rules={[
          { required: true, message: '请确认密码' },
          ({ getFieldValue }) => ({
            validator(_, value) {
              if (!value || getFieldValue('password') === value) {
                return Promise.resolve();
              }
              return Promise.reject(new Error('两次输入的密码不一致'));
            },
          }),
        ]}
      >
        <Input.Password prefix={<LockOutlined />} placeholder="再次输入密码" />
      </Form.Item>
      <Form.Item>
        <Button type="primary" htmlType="submit" loading={submitting} block>注册</Button>
      </Form.Item>
      <div style={{ textAlign: 'center' }}>
        已有账号？<Link to="/login">去登录 →</Link>
      </div>
    </Form>
  );
}

export default Register;

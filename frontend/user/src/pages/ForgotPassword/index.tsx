import { useNavigate } from 'react-router-dom';
import { Result, Button } from 'antd';

function ForgotPassword() {
  const navigate = useNavigate();
  return (
    <Result
      status="info"
      title="找回密码"
      subTitle="找回密码功能即将上线，敬请期待"
      extra={<Button type="primary" onClick={() => navigate('/login')}>返回登录</Button>}
    />
  );
}

export default ForgotPassword;

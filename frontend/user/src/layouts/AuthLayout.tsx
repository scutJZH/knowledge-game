import { Outlet, Navigate, useSearchParams } from 'react-router-dom';
import { useAuthStore } from '@/store/auth-store';
import './AuthLayout.css';

function AuthLayout() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated());
  const [searchParams] = useSearchParams();

  if (isAuthenticated) {
    const raw = searchParams.get('redirect');
    const to = raw && raw.startsWith('/') && !raw.startsWith('//') ? raw : '/groups';
    return <Navigate to={to} replace />;
  }

  return (
    <div className="auth-layout">
      <div className="brand-area">
        <div className="brand-logo">🃏</div>
        <div className="brand-title">Knowledge Game</div>
        <div className="brand-slogan">收集 · 升星 · 串联记忆</div>
        <div className="brand-slogan">把知识点变成你想要的卡牌</div>
      </div>
      <div className="form-area">
        <div className="form-container">
          <Outlet />
        </div>
      </div>
    </div>
  );
}

export default AuthLayout;

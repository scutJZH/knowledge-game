import { Outlet, Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/store/auth-store';

function AuthGuard() {
  const location = useLocation();
  const isAuthenticated = useAuthStore((s) => !!s.accessToken);

  if (!isAuthenticated) {
    return <Navigate to={`/login?redirect=${encodeURIComponent(location.pathname)}`} replace />;
  }

  return <Outlet />;
}

export default AuthGuard;

import { createBrowserRouter, Navigate } from 'react-router-dom';
import MainLayout from '@/layouts/MainLayout';
import AuthGuard from '@/components/AuthGuard';
import Home from '@/pages/Home';
import NotFound from '@/pages/NotFound';

const router = createBrowserRouter([
  {
    path: '/',
    element: <Navigate to="/home" replace />,
  },
  {
    path: '/login',
    element: <div style={{ padding: 48, textAlign: 'center', color: '#999' }}>登录页面（REQ-28 实现）</div>,
  },
  {
    path: '/register',
    element: <div style={{ padding: 48, textAlign: 'center', color: '#999' }}>注册页面（REQ-28 实现）</div>,
  },
  {
    element: <AuthGuard />,
    children: [
      {
        element: <MainLayout />,
        children: [
          {
            path: '/home',
            element: <Home />,
          },
          {
            path: '*',
            element: <NotFound />,
          },
        ],
      },
    ],
  },
]);

export default router;

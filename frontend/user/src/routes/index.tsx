import { createBrowserRouter, Navigate } from 'react-router-dom';
import { lazy } from 'react';
import MainLayout from '@/layouts/MainLayout';
import AuthLayout from '@/layouts/AuthLayout';
import AuthGuard from '@/components/AuthGuard';
import Home from '@/pages/Home';
import NotFound from '@/pages/NotFound';
import Login from '@/pages/Login';
import Register from '@/pages/Register';
import ForgotPassword from '@/pages/ForgotPassword';

const GroupList = lazy(() => import('@/pages/GroupList'));

const router = createBrowserRouter([
  {
    path: '/',
    element: <Navigate to="/home" replace />,
  },
  {
    element: <AuthLayout />,
    children: [
      { path: '/login', element: <Login /> },
      { path: '/register', element: <Register /> },
      { path: '/forgot-password', element: <ForgotPassword /> },
    ],
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
            path: '/groups',
            element: <GroupList />,
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

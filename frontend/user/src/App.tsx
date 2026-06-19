import { ConfigProvider } from 'antd';
import { RouterProvider } from 'react-router-dom';
import router from '@/routes';
import appTheme from '@/styles/theme';

function App() {
  return (
    <ConfigProvider theme={appTheme}>
      <RouterProvider router={router} />
    </ConfigProvider>
  );
}

export default App;

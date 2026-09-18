import React from 'react';
import { Button, Result } from 'antd';
import { useNavigate } from 'react-router-dom';

const Forbidden: React.FC = () => {
  const navigate = useNavigate();
  return (
    <Result
      status="403"
      title="403"
      subTitle="抱歉，您没有访问该页面的权限。"
      extra={
        <Button type="primary" onClick={() => navigate('/workbench')}>
          返回工作台
        </Button>
      }
    />
  );
};

export default Forbidden;

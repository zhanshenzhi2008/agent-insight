import React from 'react';
import { message } from 'antd';
import { useAuth } from '../../auth/AuthContext';

export interface RequireAuthProps {
  children: React.ReactNode;
  permission?: string;
  role?: string;
}

const RequireAuth: React.FC<RequireAuthProps> = ({ children, permission, role }) => {
  const { hasPermission, hasRole } = useAuth();

  if (permission && !hasPermission(permission)) {
    message.error('没有访问权限');
    console.warn('[RequireAuth] Permission denied:', permission);
    return null;
  }

  if (role && !hasRole(role)) {
    message.error('没有访问权限');
    console.warn('[RequireAuth] Role denied:', role);
    return null;
  }

  return <>{children}</>;
};

export default RequireAuth;

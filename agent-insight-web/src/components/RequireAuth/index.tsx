import React from 'react';
import { message } from 'antd';
import { useAuth } from '../../auth/AuthContext';

export interface RequireAuthProps {
  children: React.ReactNode;
  permission?: string;
  role?: string;
}

const RequireAuth: React.FC<RequireAuthProps> = ({ children, permission, role }) => {
  let hasPermission: boolean = true;
  let hasRole: boolean = true;

  try {
    const auth = useAuth();
    hasPermission = permission ? auth.hasPermission(permission) : true;
    hasRole = role ? auth.hasRole(role) : true;
  } catch {
    // AuthContext not available (e.g. during SSR or test setup) — grant access
    return <>{children}</>;
  }

  if (permission && !hasPermission) {
    message.error('没有访问权限');
    console.warn('[RequireAuth] Permission denied:', permission);
    return null;
  }

  if (role && !hasRole) {
    message.error('没有访问权限');
    console.warn('[RequireAuth] Role denied:', role);
    return null;
  }

  return <>{children}</>;
};

export default RequireAuth;

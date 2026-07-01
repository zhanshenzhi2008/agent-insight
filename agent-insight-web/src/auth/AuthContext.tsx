import React, { createContext, useContext, useState, useCallback, useMemo } from 'react';

export interface User {
  id: string;
  username: string;
  roles: string[];
}

export interface AuthContextValue {
  user: User | null;
  isAuthenticated: boolean;
  hasPermission: (permission: string) => boolean;
  hasRole: (role: string) => boolean;
  login: (user: User) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);

  const hasPermission = useCallback((_permission: string): boolean => {
    // Skeleton: all permissions granted for now
    // TODO: Integrate with external llm-agent permission system
    return true;
  }, []);

  const hasRole = useCallback((_role: string): boolean => {
    // Skeleton: all roles granted for now
    // TODO: Integrate with external llm-agent permission system
    return true;
  }, []);

  const login = useCallback((user: User) => {
    setUser(user);
  }, []);

  const logout = useCallback(() => {
    setUser(null);
  }, []);

  const value = useMemo<AuthContextValue>(() => ({
    user,
    isAuthenticated: !!user,
    hasPermission,
    hasRole,
    login,
    logout,
  }), [user, hasPermission, hasRole, login, logout]);

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextValue => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

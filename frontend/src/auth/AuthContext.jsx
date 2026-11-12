import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import api from '../services/api';

const AuthContext = createContext(null);
export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    try { return JSON.parse(sessionStorage.getItem('transitpulse_user') || 'null'); } catch { return null; }
  });
  const [loading, setLoading] = useState(Boolean(sessionStorage.getItem('transitpulse_token')));
  useEffect(() => {
    let active = true;
    const token = sessionStorage.getItem('transitpulse_token');
    if (!token) { setLoading(false); return undefined; }
    api.me().then((profile) => {
      if (active) { setUser(profile); sessionStorage.setItem('transitpulse_user', JSON.stringify(profile)); }
    }).catch(() => {
      if (active) { sessionStorage.removeItem('transitpulse_token'); sessionStorage.removeItem('transitpulse_user'); setUser(null); }
    }).finally(() => { if (active) setLoading(false); });
    const onUnauthorized = () => { if (active) { setUser(null); setLoading(false); } };
    window.addEventListener('transitpulse:unauthorized', onUnauthorized);
    return () => { active = false; window.removeEventListener('transitpulse:unauthorized', onUnauthorized); };
  }, []);
  const accept = (result) => {
    sessionStorage.setItem('transitpulse_token', result.token);
    sessionStorage.setItem('transitpulse_user', JSON.stringify(result.user));
    setUser(result.user);
  };
  const login = async (credentials) => accept(await api.login(credentials));
  const register = async (details) => accept(await api.register(details));
  const logout = () => { sessionStorage.removeItem('transitpulse_token'); sessionStorage.removeItem('transitpulse_user'); setUser(null); };
  const value = useMemo(() => ({ user, loading, login, register, logout }), [user, loading]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside AuthProvider');
  return value;
}

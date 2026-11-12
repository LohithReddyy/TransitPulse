import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import Loading from './Loading';

export default function ProtectedRoute() {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <div className="auth-loading"><Loading /></div>;
  return user ? <Outlet /> : <Navigate to="/login" replace state={{ from: location }} />;
}

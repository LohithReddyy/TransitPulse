import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export default function Login() {
  const { login, user } = useAuth();
  const navigate = useNavigate(); const location = useLocation();
  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  if (user) return <Navigate to="/" replace />;
  const change = (e) => setForm({ ...form, [e.target.name]: e.target.value });
  const submit = async (e) => {
    e.preventDefault(); setError(''); setBusy(true);
    try { await login(form); navigate(location.state?.from?.pathname || '/', { replace: true }); }
    catch (err) { setError(err.message || 'Unable to sign in. Check your details.'); }
    finally { setBusy(false); }
  };
  return <main className="auth-page"><section className="auth-card">
    <div className="auth-brand"><div className="sidebar-logo">TP</div><div><strong>TransitPulse</strong><span>Transit intelligence platform</span></div></div>
    <p className="auth-eyebrow">WELCOME BACK</p><h1>Sign in to your workspace</h1><p className="auth-subtitle">Monitor service performance, delay propagation and passenger impact.</p>
    {error && <div className="auth-error" role="alert">{error}</div>}
    <form onSubmit={submit} className="auth-form">
      <label>Email address<input name="email" type="email" autoComplete="email" value={form.email} onChange={change} required placeholder="you@example.com" /></label>
      <label>Password<input name="password" type="password" autoComplete="current-password" value={form.password} onChange={change} required placeholder="Enter your password" /></label>
      <button className="auth-submit" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
    </form><p className="auth-switch">New to TransitPulse? <Link to="/register">Create an account</Link></p>
  </section></main>;
}

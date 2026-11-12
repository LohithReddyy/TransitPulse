import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export default function Register() {
  const { register, user } = useAuth(); const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', password: '', confirmPassword: '' });
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  if (user) return <Navigate to="/" replace />;
  const change = (e) => setForm({ ...form, [e.target.name]: e.target.value });
  const submit = async (e) => {
    e.preventDefault(); setError('');
    if (form.password !== form.confirmPassword) { setError('Passwords do not match.'); return; }
    if (form.password.length < 10) { setError('Use at least 10 characters for your password.'); return; }
    setBusy(true);
    try { await register({ fullName: form.fullName, email: form.email, password: form.password }); navigate('/', { replace: true }); }
    catch (err) { setError(err.message || 'Unable to create your account.'); }
    finally { setBusy(false); }
  };
  return <main className="auth-page"><section className="auth-card">
    <div className="auth-brand"><div className="sidebar-logo">TP</div><div><strong>TransitPulse</strong><span>Transit intelligence platform</span></div></div>
    <p className="auth-eyebrow">GET STARTED</p><h1>Create your account</h1><p className="auth-subtitle">Set up your TransitPulse workspace access.</p>
    {error && <div className="auth-error" role="alert">{error}</div>}
    <form onSubmit={submit} className="auth-form">
      <label>Full name<input name="fullName" autoComplete="name" value={form.fullName} onChange={change} required minLength={2} maxLength={120} placeholder="Your name" /></label>
      <label>Email address<input name="email" type="email" autoComplete="email" value={form.email} onChange={change} required placeholder="you@example.com" /></label>
      <label>Password<input name="password" type="password" autoComplete="new-password" value={form.password} onChange={change} required minLength={10} maxLength={72} placeholder="At least 10 characters" /></label>
      <label>Confirm password<input name="confirmPassword" type="password" autoComplete="new-password" value={form.confirmPassword} onChange={change} required placeholder="Re-enter password" /></label>
      <button className="auth-submit" disabled={busy}>{busy ? 'Creating account…' : 'Create account'}</button>
    </form><p className="auth-switch">Already registered? <Link to="/login">Sign in</Link></p>
  </section></main>;
}

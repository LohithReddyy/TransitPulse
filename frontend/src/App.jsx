import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import Layout from './components/Layout';
import Analytics from './pages/Analytics';
import ConnectionRisk from './pages/ConnectionRisk';
import Dashboard from './pages/Dashboard';
import DelayInvestigation from './pages/DelayInvestigation';
import Incidents from './pages/Incidents';
import PassengerImpact from './pages/PassengerImpact';
import Routes_ from './pages/RoutesPage';

import Stops from './pages/Stops';
import Login from './pages/Login';
import Register from './pages/Register';

export default function App() {
  return <BrowserRouter><AuthProvider><Routes>
    <Route path="/login" element={<Login />} />
    <Route path="/register" element={<Register />} />
    <Route element={<ProtectedRoute />}><Route element={<Layout />}>
      <Route index element={<Dashboard />} />
      <Route path="/routes" element={<Routes_ />} />
      <Route path="/stops" element={<Stops />} />
      <Route path="/delay-investigation" element={<DelayInvestigation />} />
      <Route path="/connection-risk" element={<ConnectionRisk />} />
      <Route path="/incidents" element={<Incidents />} />
      <Route path="/passenger-impact" element={<PassengerImpact />} />
      <Route path="/analytics" element={<Analytics />} />
    </Route></Route>
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes></AuthProvider></BrowserRouter>;
}

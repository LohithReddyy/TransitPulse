import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import Analytics from './pages/Analytics';
import ConnectionRisk from './pages/ConnectionRisk';
import Dashboard from './pages/Dashboard';
import DelayInvestigation from './pages/DelayInvestigation';
import Incidents from './pages/Incidents';
import PassengerImpact from './pages/PassengerImpact';
import Routes_ from './pages/RoutesPage';
import Settings from './pages/Settings';
import Stops from './pages/Stops';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<Dashboard />} />
          <Route path="/routes" element={<Routes_ />} />
          <Route path="/stops" element={<Stops />} />
          <Route path="/delay-investigation" element={<DelayInvestigation />} />
          <Route path="/connection-risk" element={<ConnectionRisk />} />
          <Route path="/incidents" element={<Incidents />} />
          <Route path="/passenger-impact" element={<PassengerImpact />} />
          <Route path="/analytics" element={<Analytics />} />
          <Route path="/settings" element={<Settings />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

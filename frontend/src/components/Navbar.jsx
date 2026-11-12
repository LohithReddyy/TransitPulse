
import { useAuth } from '../auth/AuthContext';
import { useNavigate } from 'react-router-dom';

const TITLES = {
  '/': ['Dashboard', 'Network-wide delay and passenger impact overview'],
  '/routes': ['Routes', 'Route inventory and operational performance'],
  '/stops': ['Stops', 'Stops, passenger volume and delay frequency'],
  '/delay-investigation': [
    'Delay Investigation',
    'Scheduled versus actual, with traceable propagation',
  ],
  '/connection-risk': [
    'Connection Risk Analysis',
    'Transfer buffers, missed connections and sensitivity',
  ],
  '/incidents': ['Incident Management', 'Operator disruption annotations'],
  '/passenger-impact': [
    'Passenger Impact',
    'Estimated passengers affected by delay and missed transfers',
  ],
  '/analytics': [
    'Analytics',
    'Performance, distribution, propagation and recovery',
  ],
};

export default function Navbar({ pathname, onToggleMenu }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const [title, subtitle] = TITLES[pathname] || ['TransitPulse', ''];

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <header className="navbar">
      <div className="navbar-right">
        <button
          type="button"
          className="menu-toggle"
          onClick={onToggleMenu}
          aria-label="Toggle navigation"
        >
          ☰
        </button>

        <div className="navbar-title">
          <h1>{title}</h1>
          <p>{subtitle}</p>
        </div>
      </div>

      <div className="navbar-right">
        <div className="navbar-user">
          <span>{user?.fullName || user?.email}</span>

          <button
            type="button"
            className="logout-button"
            onClick={handleLogout}
          >
            Sign out
          </button>
        </div>
      </div>
    </header>
  );
}

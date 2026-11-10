import { useEffect, useState } from 'react';
import api from '../services/api';

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
  '/analytics': ['Analytics', 'Performance, distribution, propagation and recovery'],
  '/settings': ['Settings', 'Analytical parameters used by the engine'],
};

export default function Navbar({ pathname, onToggleMenu }) {
  const [engineReady, setEngineReady] = useState(null);

  useEffect(() => {
    let cancelled = false;
    api
      .getEngineStatus()
      .then((status) => {
        if (!cancelled) {
          setEngineReady(Boolean(status?.payloads?.length));
        }
      })
      .catch(() => {
        if (!cancelled) setEngineReady(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const [title, subtitle] = TITLES[pathname] || ['TransitPulse', ''];

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
        {engineReady === false && (
          <span
            className="badge badge-warn"
            title="Run: cd data-engine && python -m transitpulse_engine.pipeline"
          >
            engine not run
          </span>
        )}
        {engineReady === true && (
          <span className="badge badge-ok" title="Analytics are available">
            analytics ready
          </span>
        )}
      </div>
    </header>
  );
}

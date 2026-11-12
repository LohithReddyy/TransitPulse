import { NavLink } from 'react-router-dom';

const NAV_SECTIONS = [
  {
    label: 'Overview',
    items: [
      { to: '/', label: 'Dashboard', icon: '▣', end: true },
      { to: '/analytics', label: 'Analytics', icon: '◴' },
    ],
  },
  {
    label: 'Network',
    items: [
      { to: '/routes', label: 'Routes', icon: '⇉' },
      { to: '/stops', label: 'Stops', icon: '◉' },
    ],
  },
  {
    label: 'Investigation',
    items: [
      { to: '/delay-investigation', label: 'Delay Investigation', icon: '◎' },
      { to: '/connection-risk', label: 'Connection Risk', icon: '⇄' },
      { to: '/passenger-impact', label: 'Passenger Impact', icon: '☺' },
      { to: '/incidents', label: 'Incident Management', icon: '⚠' },
    ],
  }
];

export default function Sidebar({ open, onNavigate }) {
  return (
    <aside className={`sidebar ${open ? 'open' : ''}`.trim()}>
      <div className="sidebar-brand">
        <div className="sidebar-logo" aria-hidden="true">TP</div>
        <div className="sidebar-brand-text">
          <strong>TransitPulse</strong>
          <span>Delay Analytics</span>
        </div>
      </div>

      <nav className="sidebar-nav">
        {NAV_SECTIONS.map((section) => (
          <div key={section.label}>
            <div className="sidebar-section-label">{section.label}</div>
            {section.items.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  `nav-item ${isActive ? 'active' : ''}`.trim()
                }
                onClick={onNavigate}
              >
                <span className="nav-icon" aria-hidden="true">{item.icon}</span>
                <span>{item.label}</span>
              </NavLink>
            ))}
          </div>
        ))}
      </nav>

    </aside>
  );
}

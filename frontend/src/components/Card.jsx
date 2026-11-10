export default function Card({ title, subtitle, actions, children, className = '' }) {
  return (
    <section className={`card ${className}`.trim()}>
      {(title || actions) && (
        <header className="card-header">
          <div>
            {title && <h2>{title}</h2>}
            {subtitle && <p className="card-subtitle">{subtitle}</p>}
          </div>
          {actions && <div className="navbar-right">{actions}</div>}
        </header>
      )}
      {children}
    </section>
  );
}

import { formatNumber } from '../services/format';

/**
 * A single headline metric.
 *
 * `estimate` renders an EST tag so modelled passenger figures are never
 * mistaken for measured counts.
 */
export default function KpiCard({
  label,
  value,
  unit,
  hint,
  decimals = 0,
  estimate = false,
}) {
  const display =
    typeof value === 'number' ? formatNumber(value, decimals) : value ?? '—';

  return (
    <div className="kpi">
      <div className="kpi-label">
        {label}
        {estimate && (
          <span className="estimate-tag" title="Modelled estimate, not a measured count">
            est
          </span>
        )}
      </div>
      <div className="kpi-value">
        {display}
        {unit && <span className="kpi-unit">{unit}</span>}
      </div>
      {hint && <div className="kpi-hint">{hint}</div>}
    </div>
  );
}

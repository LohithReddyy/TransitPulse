/** The facts supporting a propagation claim, shown so users can judge it. */
export default function EvidenceList({ evidence, title = 'Evidence' }) {
  const items = Array.isArray(evidence)
    ? evidence
    : typeof evidence === 'string' && evidence
      ? evidence.split(' | ')
      : [];

  if (items.length === 0) return null;

  return (
    <div>
      <div className="kpi-label" style={{ marginTop: '0.5rem' }}>{title}</div>
      <ul className="evidence-list">
        {items.map((item, index) => (
          <li key={index}>{item}</li>
        ))}
      </ul>
    </div>
  );
}

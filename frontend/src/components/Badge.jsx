/** Status pill. The class name drives the colour, so labels stay consistent. */
export default function Badge({ value, label, title }) {
  if (value === null || value === undefined || value === '') {
    return <span className="badge badge-unknown">n/a</span>;
  }
  const key = String(value).replace(/\s+/g, '_').toUpperCase();
  return (
    <span className={`badge badge-${key}`} title={title || String(value)}>
      {label ?? String(value).replace(/_/g, ' ')}
    </span>
  );
}

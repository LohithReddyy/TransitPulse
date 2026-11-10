export default function EmptyState({
  message = 'Nothing to show here.',
  hint,
  action,
}) {
  return (
    <div className="state">
      <span>{message}</span>
      {hint && <span style={{ fontSize: '0.8rem', color: 'var(--text-dim)' }}>{hint}</span>}
      {action}
    </div>
  );
}

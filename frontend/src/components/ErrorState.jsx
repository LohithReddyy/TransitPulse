/**
 * Error display.
 *
 * The backend writes actionable messages (for example, telling the user to
 * run the Python engine), so the server's message is shown verbatim rather
 * than replaced with a generic string.
 */
export default function ErrorState({ error, onRetry, title = 'Something went wrong' }) {
  const message =
    typeof error === 'string' ? error : error?.message || 'Unknown error';

  return (
    <div className="state state-error" role="alert">
      <strong>{title}</strong>
      <pre>{message}</pre>
      {onRetry && (
        <button type="button" className="btn btn-secondary btn-sm" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}

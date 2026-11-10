import { useState } from 'react';
import Card from '../components/Card';
import ErrorState from '../components/ErrorState';
import Loading from '../components/Loading';
import api from '../services/api';
import useApi from '../services/useApi';

const CATEGORY_BLURB = {
  DELAY:
    'Thresholds that classify a delay. They must stay strictly increasing, so the API rejects any change that would invert them.',
  PROPAGATION:
    'How far the engine looks for knock-on effects, and the turnaround a vehicle needs between trips.',
  CONNECTION:
    'The buffer passengers need to change services. Connection risk is sensitive to this value, so the Connection Risk page reports it across a range.',
  PASSENGER:
    'Assumptions behind passenger estimates. Changing these changes every passenger figure in the application, all of which remain estimates.',
  DATA_QUALITY:
    'How gaps in the data are handled. Missing GPS events are never treated as zero delay; the only choice is whether to exclude them or interpolate and label them as estimated.',
  SENSITIVITY: 'The transfer buffers tested by the sensitivity analysis.',
  GENERAL: 'Other parameters.',
};

export default function Settings() {
  const grouped = useApi(() => api.getConfigGrouped(), []);
  const [drafts, setDrafts] = useState({});
  const [savingKey, setSavingKey] = useState(null);
  const [errors, setErrors] = useState({});
  const [message, setMessage] = useState(null);

  const setDraft = (key, value) =>
    setDrafts((current) => ({ ...current, [key]: value }));

  const save = async (item) => {
    const value = drafts[item.configKey] ?? item.configValue;
    setSavingKey(item.configKey);
    setErrors((current) => ({ ...current, [item.configKey]: null }));
    setMessage(null);
    try {
      await api.updateConfig(item.configKey, String(value));
      setMessage(
        `Saved ${item.configKey}. Re-run the data engine to apply it to the analytics.`
      );
      setDrafts((current) => {
        const next = { ...current };
        delete next[item.configKey];
        return next;
      });
      grouped.reload();
    } catch (error) {
      setErrors((current) => ({ ...current, [item.configKey]: error }));
    } finally {
      setSavingKey(null);
    }
  };

  if (grouped.loading) return <Loading label="Loading configuration…" />;
  if (grouped.error) {
    return <ErrorState error={grouped.error} onRetry={grouped.reload} />;
  }

  const categories = Object.entries(grouped.data || {});

  return (
    <>
      <p className="page-intro">
        Analytical parameters used by the delay, propagation, connection-risk
        and passenger-impact engines. Values are bounded, and changes that would
        make an analysis meaningless are rejected with an explanation rather
        than silently accepted.
      </p>

      <div className="notice">
        Saving a value here updates the stored configuration. The Python engine
        reads it on its next run, so re-run{' '}
        <code>python -m transitpulse_engine.pipeline</code> to recompute the
        analytics with the new parameters.
      </div>

      {message && <div className="notice">{message}</div>}

      {categories.length === 0 && (
        <Card title="No parameters found">
          <p className="page-intro" style={{ marginBottom: 0 }}>
            The configuration table is empty. Start the backend with the dev
            profile to seed the defaults, or run the MySQL seed script.
          </p>
        </Card>
      )}

      {categories.map(([category, items]) => (
        <Card
          key={category}
          title={category.replace(/_/g, ' ')}
          subtitle={CATEGORY_BLURB[category] || CATEGORY_BLURB.GENERAL}
        >
          {items.map((item) => {
            const draft = drafts[item.configKey];
            const dirty = draft !== undefined && draft !== item.configValue;
            const error = errors[item.configKey];

            return (
              <div
                key={item.configKey}
                style={{
                  borderBottom: '1px solid var(--border-soft)',
                  paddingBottom: '0.75rem',
                  marginBottom: '0.75rem',
                }}
              >
                <div className="field" style={{ marginBottom: '0.3rem' }}>
                  <label htmlFor={`cfg-${item.configKey}`}>
                    {item.configKey}
                  </label>
                  <div style={{ display: 'flex', gap: '0.5rem' }}>
                    {item.valueType === 'STRING' &&
                    item.configKey === 'missing_gps_strategy' ? (
                      <select
                        id={`cfg-${item.configKey}`}
                        value={draft ?? item.configValue}
                        onChange={(event) =>
                          setDraft(item.configKey, event.target.value)
                        }
                      >
                        <option value="EXCLUDE">
                          EXCLUDE — leave gaps out of the statistics
                        </option>
                        <option value="ESTIMATE">
                          ESTIMATE — interpolate and label as estimated
                        </option>
                      </select>
                    ) : item.valueType === 'NUMBER' ? (
                      <input
                        id={`cfg-${item.configKey}`}
                        type="number"
                        step="any"
                        min={item.minValue ?? undefined}
                        max={item.maxValue ?? undefined}
                        value={draft ?? item.configValue}
                        onChange={(event) =>
                          setDraft(item.configKey, event.target.value)
                        }
                      />
                    ) : (
                      <input
                        id={`cfg-${item.configKey}`}
                        type="text"
                        value={draft ?? item.configValue}
                        onChange={(event) =>
                          setDraft(item.configKey, event.target.value)
                        }
                      />
                    )}
                    <button
                      type="button"
                      className="btn btn-sm"
                      disabled={!dirty || savingKey === item.configKey}
                      onClick={() => save(item)}
                    >
                      {savingKey === item.configKey ? 'Saving…' : 'Save'}
                    </button>
                  </div>
                  <div className="field-hint">
                    {item.description}
                    {(item.minValue != null || item.maxValue != null) && (
                      <>
                        {' '}
                        (allowed range: {item.minValue ?? '−∞'} to{' '}
                        {item.maxValue ?? '∞'})
                      </>
                    )}
                  </div>
                  {error && <div className="field-error">{error.message}</div>}
                </div>
              </div>
            );
          })}
        </Card>
      ))}
    </>
  );
}

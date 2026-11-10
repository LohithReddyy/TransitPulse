/**
 * Formatting helpers.
 *
 * The central rule: null and undefined render as an em dash, never as 0.
 * A missing GPS observation must never look like an on-time arrival.
 */

export function formatNumber(value, decimals = 0) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '—';
  }
  return Number(value).toLocaleString(undefined, {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
}

export function formatMinutes(value, decimals = 1) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '—';
  }
  const n = Number(value);
  const sign = n > 0 ? '+' : '';
  return `${sign}${n.toFixed(decimals)} min`;
}

export function formatPercent(value, decimals = 1) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '—';
  }
  return `${Number(value).toFixed(decimals)}%`;
}

/** "09:15" from an ISO timestamp, or an em dash when absent. */
export function formatTime(value) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

export function formatDateTime(value) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString([], {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function formatDate(value) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleDateString([], {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
  });
}

/** Convert an object of counts into Recharts-friendly rows. */
export function toChartData(record, nameKey = 'name', valueKey = 'value') {
  if (!record || typeof record !== 'object') return [];
  return Object.entries(record).map(([key, value]) => ({
    [nameKey]: key,
    [valueKey]: Number(value) || 0,
  }));
}

export const DELAY_CLASS_COLORS = {
  EARLY: '#8b5cf6',
  ON_TIME: '#22c55e',
  MINOR: '#eab308',
  MODERATE: '#f97316',
  SEVERE: '#ef4444',
  MISSING: '#64748b',
};

export const CHART_COLORS = [
  '#22d3ee',
  '#2f6fed',
  '#a78bfa',
  '#f97316',
  '#22c55e',
  '#eab308',
  '#f87171',
  '#38bdf8',
];

export const CHART_AXIS = {
  stroke: '#6f87a6',
  fontSize: 11,
};

export const CHART_GRID_COLOR = '#1d3a5c';

export const CHART_TOOLTIP_STYLE = {
  contentStyle: {
    background: '#09162b',
    border: '1px solid #27507d',
    borderRadius: '6px',
    fontSize: '0.8rem',
  },
  labelStyle: { color: '#f2f7ff' },
};

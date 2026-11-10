import { describe, expect, it } from 'vitest';
import {
  formatMinutes,
  formatNumber,
  formatPercent,
  formatTime,
  toChartData,
} from '../services/format';

describe('formatNumber', () => {
  it('renders null and undefined as an em dash, never as zero', () => {
    expect(formatNumber(null)).toBe('—');
    expect(formatNumber(undefined)).toBe('—');
    expect(formatNumber(NaN)).toBe('—');
  });

  it('renders zero as zero', () => {
    expect(formatNumber(0)).toBe('0');
  });

  it('respects the requested decimal places', () => {
    expect(formatNumber(3.14159, 2)).toBe('3.14');
  });
});

describe('formatMinutes', () => {
  it('does not turn a missing delay into 0 min', () => {
    expect(formatMinutes(null)).toBe('—');
    expect(formatMinutes(undefined)).toBe('—');
  });

  it('signs a positive delay so lateness is unambiguous', () => {
    expect(formatMinutes(5)).toBe('+5.0 min');
    expect(formatMinutes(-3)).toBe('-3.0 min');
    expect(formatMinutes(0)).toBe('0.0 min');
  });
});

describe('formatPercent', () => {
  it('handles missing values', () => {
    expect(formatPercent(null)).toBe('—');
  });

  it('formats a percentage', () => {
    expect(formatPercent(68.654, 1)).toBe('68.7%');
  });
});

describe('formatTime', () => {
  it('renders a missing timestamp as an em dash', () => {
    expect(formatTime(null)).toBe('—');
    expect(formatTime('')).toBe('—');
  });

  it('passes through an unparseable value rather than inventing a time', () => {
    expect(formatTime('not a date')).toBe('not a date');
  });
});

describe('toChartData', () => {
  it('converts a count map into chart rows', () => {
    expect(toChartData({ ON_TIME: 3, SEVERE: 1 }, 'delayClass', 'count')).toEqual([
      { delayClass: 'ON_TIME', count: 3 },
      { delayClass: 'SEVERE', count: 1 },
    ]);
  });

  it('returns an empty array for null input', () => {
    expect(toChartData(null)).toEqual([]);
  });
});

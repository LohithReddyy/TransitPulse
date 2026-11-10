import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import userEvent from '@testing-library/user-event';
import Badge from '../components/Badge';
import CalculationSteps from '../components/CalculationSteps';
import DataTable from '../components/DataTable';
import ErrorState from '../components/ErrorState';
import EvidenceList from '../components/EvidenceList';
import KpiCard from '../components/KpiCard';
import Loading from '../components/Loading';

describe('Badge', () => {
  it('renders a readable label with the status class', () => {
    render(<Badge value="AT_RISK" />);
    const badge = screen.getByText('AT RISK');
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveClass('badge-AT_RISK');
  });

  it('renders n/a for a missing value instead of a blank pill', () => {
    render(<Badge value={null} />);
    expect(screen.getByText('n/a')).toBeInTheDocument();
  });
});

describe('KpiCard', () => {
  it('shows an em dash when the value is missing', () => {
    render(<KpiCard label="Average delay" value={null} />);
    expect(screen.getByText('—')).toBeInTheDocument();
  });

  it('marks estimated figures so they are not read as measurements', () => {
    render(<KpiCard label="Passengers affected" value={1200} estimate />);
    expect(screen.getByText('est')).toBeInTheDocument();
    expect(screen.getByText('1,200')).toBeInTheDocument();
  });
});

describe('DataTable', () => {
  const columns = [
    { key: 'name', header: 'Name' },
    { key: 'delay', header: 'Delay', numeric: true },
  ];

  it('renders rows', () => {
    render(
      <DataTable
        columns={columns}
        rows={[{ name: 'Civic Centre', delay: 5 }]}
        rowKey={(row) => row.name}
      />
    );
    expect(screen.getByText('Civic Centre')).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
  });

  it('renders a null cell as an em dash rather than zero', () => {
    render(
      <DataTable
        columns={columns}
        rows={[{ name: 'Lantern Row', delay: null }]}
        rowKey={(row) => row.name}
      />
    );
    expect(screen.getByText('—')).toBeInTheDocument();
    expect(screen.queryByText('0')).not.toBeInTheDocument();
  });

  it('shows the empty state when there are no rows', () => {
    render(
      <DataTable columns={columns} rows={[]} emptyMessage="No connections found." />
    );
    expect(screen.getByText('No connections found.')).toBeInTheDocument();
  });

  it('calls onRowClick with the clicked row', async () => {
    const onRowClick = vi.fn();
    render(
      <DataTable
        columns={columns}
        rows={[{ name: 'Northgate', delay: 2 }]}
        rowKey={(row) => row.name}
        onRowClick={onRowClick}
      />
    );
    await userEvent.click(screen.getByText('Northgate'));
    expect(onRowClick).toHaveBeenCalledWith({ name: 'Northgate', delay: 2 });
  });
});

describe('ErrorState', () => {
  it("shows the server's own actionable message", () => {
    render(
      <ErrorState
        error={new Error('Run: python -m transitpulse_engine.pipeline')}
      />
    );
    expect(
      screen.getByText(/python -m transitpulse_engine\.pipeline/)
    ).toBeInTheDocument();
  });

  it('offers a retry when a handler is supplied', async () => {
    const onRetry = vi.fn();
    render(<ErrorState error="boom" onRetry={onRetry} />);
    await userEvent.click(screen.getByRole('button', { name: /try again/i }));
    expect(onRetry).toHaveBeenCalled();
  });
});

describe('CalculationSteps', () => {
  it('shows the arithmetic for each step so it can be checked by hand', () => {
    render(
      <CalculationSteps
        steps={[
          {
            step: 1,
            description: 'Delay at the final stop',
            calculation: '09:28 - 09:20 = 8 min',
          },
        ]}
      />
    );
    expect(screen.getByText('Delay at the final stop')).toBeInTheDocument();
    expect(screen.getByText('09:28 - 09:20 = 8 min')).toBeInTheDocument();
  });

  it('renders nothing when there are no steps', () => {
    const { container } = render(<CalculationSteps steps={[]} />);
    expect(container).toBeEmptyDOMElement();
  });
});

describe('EvidenceList', () => {
  it('renders an array of evidence', () => {
    render(<EvidenceList evidence={['Same vehicle V003', 'Turnaround is 5 min']} />);
    expect(screen.getByText('Same vehicle V003')).toBeInTheDocument();
    expect(screen.getByText('Turnaround is 5 min')).toBeInTheDocument();
  });

  it('splits the pipe-delimited form used in CSV exports', () => {
    render(<EvidenceList evidence={'First fact | Second fact'} />);
    expect(screen.getByText('First fact')).toBeInTheDocument();
    expect(screen.getByText('Second fact')).toBeInTheDocument();
  });
});

describe('Loading', () => {
  it('announces itself to assistive technology', () => {
    render(<Loading label="Loading dashboard…" />);
    expect(screen.getByRole('status')).toHaveTextContent('Loading dashboard…');
  });
});

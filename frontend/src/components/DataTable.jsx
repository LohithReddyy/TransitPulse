import EmptyState from './EmptyState';

/**
 * Small declarative table.
 *
 * Columns describe how to render each cell, which keeps null handling in one
 * place: a null numeric value renders as an em dash rather than 0, so a
 * missing observation is never displayed as an on-time arrival.
 */
export default function DataTable({
  columns,
  rows,
  rowKey,
  onRowClick,
  selectedKey,
  emptyMessage = 'No rows to display.',
  emptyHint,
}) {
  if (!rows || rows.length === 0) {
    return <EmptyState message={emptyMessage} hint={emptyHint} />;
  }

  return (
    <div className="table-wrap">
      <table className="data">
        <thead>
          <tr>
            {columns.map((col) => (
              <th key={col.key} className={col.numeric ? 'num' : undefined}>
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => {
            const key = rowKey ? rowKey(row, index) : index;
            return (
              <tr
                key={key}
                className={[
                  onRowClick ? 'clickable' : '',
                  selectedKey !== undefined && selectedKey === key ? 'selected' : '',
                ]
                  .filter(Boolean)
                  .join(' ')}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
              >
                {columns.map((col) => (
                  <td key={col.key} className={col.numeric ? 'num' : undefined}>
                    {col.render ? col.render(row) : row[col.key] ?? '—'}
                  </td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

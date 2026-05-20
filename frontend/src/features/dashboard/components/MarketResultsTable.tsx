import { formatDateTime, formatPercent, formatPrice } from "@/lib/format";
import type { MarketScanResult } from "../types/market-dashboard";

type Props = {
  results: MarketScanResult[];
};

export function MarketResultsTable({ results }: Props) {
  if (results.length === 0) {
    return <div className="empty-state">No scan results yet.</div>;
  }

  return (
    <div className="table-frame">
      <table>
        <thead>
          <tr>
            <th>Symbol</th>
            <th>Company</th>
            <th>Current</th>
            <th>Delta</th>
            <th>Rolling</th>
            <th>Price</th>
            <th>Range</th>
            <th>Updated</th>
          </tr>
        </thead>
        <tbody>
          {results.map((result) => (
            <tr key={result.symbol} className={`row-${result.direction.toLowerCase()} ${result.alert ? "row-alert" : ""}`}>
              <td>
                <strong>{result.symbol}</strong>
              </td>
              <td>{result.companyName}</td>
              <td className={toneClass(result.currentPercent)}>{formatPercent(result.currentPercent)}</td>
              <td className={toneClass(result.delta)}>{formatPercent(result.delta)}</td>
              <td className={toneClass(result.rollingDelta)}>
                {formatPercent(result.rollingDelta)}
                <span className="window-label">{result.rollingWindowSize}</span>
              </td>
              <td>{formatPrice(result.currentPrice)}</td>
              <td>
                {formatPrice(result.low)} - {formatPrice(result.high)}
              </td>
              <td>{formatDateTime(result.updatedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function toneClass(value: number): string {
  if (value > 0) {
    return "value-positive";
  }
  if (value < 0) {
    return "value-negative";
  }
  return "value-neutral";
}

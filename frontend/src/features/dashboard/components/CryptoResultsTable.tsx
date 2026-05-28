import { formatDateTime, formatPercent, formatPrice } from "@/lib/format";
import type { CryptoScanResult } from "../types/market-dashboard";

type Props = {
  results: CryptoScanResult[];
  onOpenChart: (result: CryptoScanResult) => void;
};

export function CryptoResultsTable({ results, onOpenChart }: Props) {
  if (results.length === 0) {
    return <div className="empty-state">No crypto scan results yet.</div>;
  }

  return (
    <div className="table-frame">
      <table>
        <thead>
          <tr>
            <th>Coin</th>
            <th>Name</th>
            <th>Pair</th>
            <th>Window</th>
            <th>Move</th>
            <th>Open</th>
            <th>Close</th>
            <th>24h Volume</th>
            <th>Alert</th>
            <th>Updated</th>
          </tr>
        </thead>
        <tbody>
          {results.map((result) => (
            <tr key={result.symbol} className={`row-${result.direction.toLowerCase()} ${result.alert ? "row-alert" : ""}`}>
              <td>
                <button type="button" className="symbol-link" onClick={() => onOpenChart(result)} aria-label={`Open chart for ${result.baseAsset}`}>
                  {result.baseAsset}
                </button>
              </td>
              <td>{result.coinName}</td>
              <td>{result.symbol}</td>
              <td>{result.window}</td>
              <td className={toneClass(result.priceChangePercent)}>{formatPercent(result.priceChangePercent)}</td>
              <td>{formatCryptoPrice(result.openPrice)}</td>
              <td>{formatCryptoPrice(result.closePrice)}</td>
              <td>{formatPrice(result.quoteVolume)}</td>
              <td>{result.alert ? "Yes" : "No"}</td>
              <td>{formatDateTime(result.updatedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function formatCryptoPrice(value: number): string {
  if (value >= 1000) {
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: "USD",
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(value);
  }
  if (value >= 1) {
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: "USD",
      minimumFractionDigits: 2,
      maximumFractionDigits: 6
    }).format(value);
  }
  return `$${value.toFixed(8)}`;
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

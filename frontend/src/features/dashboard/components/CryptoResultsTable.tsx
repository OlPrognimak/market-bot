import { useMemo, useState } from "react";
import { formatDateTime, formatPercent, formatPrice } from "@/lib/format";
import { nextSort, sortButtonLabel, sortRows, type SortColumn, type SortState } from "@/lib/tableSort";
import type { CryptoScanResult } from "../types/market-dashboard";

type Props = {
  results: CryptoScanResult[];
  onOpenChart: (result: CryptoScanResult) => void;
};

export function CryptoResultsTable({ results, onOpenChart }: Props) {
  const [sort, setSort] = useState<SortState<CryptoColumnKey>>({ key: "coin", direction: "asc" });
  const sortedResults = useMemo(() => sortRows(results, sort, cryptoColumns), [results, sort]);

  if (results.length === 0) {
    return <div className="empty-state">No crypto scan results yet.</div>;
  }

  return (
    <div className="table-frame">
      <table>
        <thead>
          <tr>
            {cryptoColumns.map((column) => (
              <th key={column.key}>
                <button type="button" className="sortable-header" onClick={() => setSort((current) => nextSort(current, column.key))}>
                  {sortButtonLabel(sort, column.key, column.label)}
                </button>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {sortedResults.map((result) => (
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

type CryptoColumnKey = "coin" | "name" | "pair" | "window" | "move" | "open" | "close" | "volume" | "alert" | "updated";

const cryptoColumns: Array<SortColumn<CryptoScanResult, CryptoColumnKey>> = [
  { key: "coin", label: "Coin", value: (row) => row.baseAsset },
  { key: "name", label: "Name", value: (row) => row.coinName },
  { key: "pair", label: "Pair", value: (row) => row.symbol },
  { key: "window", label: "Window", value: (row) => row.window },
  { key: "move", label: "Move", value: (row) => row.priceChangePercent },
  { key: "open", label: "Open", value: (row) => row.openPrice },
  { key: "close", label: "Close", value: (row) => row.closePrice },
  { key: "volume", label: "24h Volume", value: (row) => row.quoteVolume },
  { key: "alert", label: "Alert", value: (row) => row.alert },
  { key: "updated", label: "Updated", value: (row) => row.updatedAt }
];

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

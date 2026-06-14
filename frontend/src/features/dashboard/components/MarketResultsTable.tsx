import { formatDateTime, formatPercent, formatPrice } from "@/lib/format";
import type { MarketScanResult } from "../types/market-dashboard";

type Props = {
  results: MarketScanResult[];
  onOpenChart: (result: MarketScanResult) => void;
};

export function MarketResultsTable({ results, onOpenChart }: Props) {
  if (results.length === 0) {
    return <div className="empty-state">No scan results yet.</div>;
  }

  return (
    <div className="table-frame market-results-frame">
      <table className="market-results-table">
        <thead>
          <tr>
            <th>Symbol</th>
            <th>Company</th>
            <th>Country</th>
            <th>Priority</th>
            <th>Current</th>
            <th>Delta</th>
            <th>Rolling</th>
            <th>Trend</th>
            <th>Price</th>
            <th>Range</th>
            <th>Updated</th>
          </tr>
        </thead>
        <tbody>
          {results.map((result) => (
            <tr key={result.symbol} className={`row-trend-${result.trend.toLowerCase()} ${result.alert ? "row-alert" : ""}`}>
              <td>
                <button type="button" className="symbol-link" onClick={() => onOpenChart(result)} aria-label={`Open chart for ${result.symbol}`}>
                  {result.symbol}
                </button>
              </td>
              <td className="metadata-cell" tabIndex={0} aria-label={metadataText(result)}>
                {companyLabel(result)}
                <span className="metadata-popover" role="tooltip">{metadataText(result)}</span>
              </td>
              <td>{result.region ?? "-"}</td>
              <td>{result.priority ?? "NORMAL"}</td>
              <td className={toneClass(result.currentPercent)}>{formatPercent(result.currentPercent)}</td>
              <td className={toneClass(result.delta)}>{formatPercent(result.delta)}</td>
              <td className={toneClass(result.rollingDelta)}>
                {formatPercent(result.rollingDelta)}
                <span className="window-label">{result.rollingWindowSize}</span>
              </td>
              <td>
                <span className={`trend-badge trend-${result.trend.toLowerCase()}`} title={trendProfileText(result)}>
                  {result.trend}
                </span>
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

function companyLabel(result: MarketScanResult): string {
  return result.region ? `${result.companyName} (${result.region})` : result.companyName;
}

function metadataText(result: MarketScanResult): string {
  return [
    `Company: ${companyLabel(result)}`,
    `Symbol: ${result.symbol}`,
    `Sector: ${result.sector ?? "-"}`,
    `Exchange: ${result.exchange ?? "-"}`,
    `Currency: ${result.currency ?? "-"}`,
    `Priority: ${result.priority ?? "NORMAL"}`
  ].join("\n");
}

function trendProfileText(result: MarketScanResult): string {
  const profile = result.trendProfile;
  if (!profile) return "Trend profile unavailable";
  return [
    `Profile: ${profile.name}`,
    `Long weight: ${(profile.longWeight * 100).toFixed(0)}%`,
    `Short weight: ${(profile.shortWeight * 100).toFixed(0)}%`,
    `Minimum score: ${profile.minimumScorePercent.toFixed(2)}%`,
    `Confidence: ${(profile.confidence * 100).toFixed(0)}%`
  ].join("\n");
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

import { formatDateTime, formatPercent, formatPrice } from "@/lib/format";
import type { ExtendedHoursSnapshot, MarketSession } from "../types/market-dashboard";

export function ExtendedHoursTable({
  snapshot,
  selectedSession
}: {
  snapshot: ExtendedHoursSnapshot | null;
  selectedSession?: MarketSession | "OVERVIEW";
}) {
  const results = snapshot?.results ?? [];
  if (results.length === 0) return <div className="empty-state">No session data is available for the selected watchlist.</div>;
  return (
    <>
      <div className={`session-data-date${snapshot?.fallback ? " fallback" : ""}`}>
        <strong>Market date: {formatMarketDate(snapshot?.dataDate)}</strong>
        {snapshot?.fallback ? <span>{fallbackMessage(snapshot, selectedSession)}</span> : null}
      </div>
      <div className="table-frame market-results-frame">
        <table className="market-results-table">
          <thead><tr>
            <th>Symbol</th><th>Company</th><th>Session</th><th>Price</th><th>Session Move</th>
            <th>Delta</th><th>Rolling</th><th>Volume</th><th>Freshness</th><th>Updated</th>
          </tr></thead>
          <tbody>{results.map((result) => (
            <tr key={`${result.symbol}-${result.session}`} className={result.freshness === "STALE" ? "row-stale" : ""}>
              <td>{result.symbol}</td><td>{result.companyName}</td>
              <td><span className={`session-badge session-${result.session.toLowerCase()}`}>{sessionLabel(result.session)}</span></td>
              <td>{formatPrice(result.price)}</td>
              <td className={tone(result.sessionMove)}>{formatPercent(result.sessionMove)}</td>
              <td className={tone(result.delta)}>{formatPercent(result.delta)}</td>
              <td className={tone(result.rolling)}>{formatPercent(result.rolling)}</td>
              <td>{new Intl.NumberFormat("en-US", { maximumFractionDigits: 0 }).format(result.volume)}</td>
              <td><span className={`freshness freshness-${result.freshness.toLowerCase()}`}>{result.freshness}</span></td>
              <td>{formatDateTime(result.providerTimestamp)}</td>
            </tr>
          ))}</tbody>
        </table>
      </div>
    </>
  );
}

function formatMarketDate(value?: string | null) {
  if (!value) return "No data";
  return new Intl.DateTimeFormat("en-US", { dateStyle: "long", timeZone: "UTC" }).format(new Date(`${value}T00:00:00Z`));
}

function sessionLabel(session: ExtendedHoursSnapshot["results"][number]["session"]) {
  return session === "PRE_MARKET" ? "PRE" : session === "POST_MARKET" ? "POST" : session === "REGULAR" ? "REG" : session;
}

function fallbackMessage(snapshot: ExtendedHoursSnapshot, selectedSession?: MarketSession | "OVERVIEW") {
  const expectedDate = formatMarketDate(snapshot.expectedDate);
  const session = selectedSession === "PRE_MARKET"
    ? "pre-market"
    : selectedSession === "POST_MARKET"
      ? "post-market"
      : "selected session";
  return `No ${session} data is available for ${expectedDate}. Showing the latest available date.`;
}

function tone(value: number) { return value > 0 ? "value-positive" : value < 0 ? "value-negative" : "value-neutral"; }

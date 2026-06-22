"use client";
import { useEffect, useMemo, useState } from "react";
import { formatDateTime, formatPercent, formatPrice } from "@/lib/format";
import { nextSort, sortButtonLabel, sortRows, type SortColumn, type SortState } from "@/lib/tableSort";
import type { AppUser } from "@/features/users/types";
import { useFuturesDashboard } from "../hooks/useFuturesDashboard";
import type { FuturesResult } from "../types/market-dashboard";

export function FuturesDashboardPage({ token, currentUser }: { token: string; currentUser: AppUser }) {
  const { snapshot, fetchSnapshot, triggerScan } = useFuturesDashboard(token);
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<SortState<FuturesColumnKey>>({ key: "contract", direction: "asc" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => { fetchSnapshot().catch((e) => setError(e instanceof Error ? e.message : "Could not load futures")); }, [fetchSnapshot]);
  const results = useMemo(() => {
    const filtered = (snapshot?.results ?? []).filter(r => [r.symbol, r.name, r.underlying, r.region].join(" ").toLowerCase().includes(query.toLowerCase()));
    return sortRows(filtered, sort, futuresColumns);
  }, [query, snapshot, sort]);
  async function scan() { setBusy(true); setError(null); try { await triggerScan(); } catch (e) { setError(e instanceof Error ? e.message : "Could not scan futures"); } finally { setBusy(false); } }
  return <main className="dashboard">
    <header className="dashboard-header"><div><h1>Futures Dashboard</h1><p>Index futures context from Yahoo. Data may be delayed.</p></div><div className="header-status"><span>Last scan: {formatDateTime(snapshot?.lastScanAt)}</span><span>{currentUser.displayName}</span></div></header>
    <section className="summary-strip">
      <FutureSummary title="Top rise" result={snapshot?.topPositive} />
      <FutureSummary title="Top drop" result={snapshot?.topNegative} />
      <div className="summary-panel"><span className="panel-title">Contracts</span><strong>{snapshot?.results.length ?? 0}</strong><span className="panel-subtitle">Yahoo market context</span></div>
    </section>
    <section className="toolbar"><input value={query} onChange={e => setQuery(e.target.value)} placeholder="Search futures" /><button className="secondary-button" onClick={() => fetchSnapshot()}>Reload</button><button onClick={scan} disabled={busy}>{busy ? "Scanning" : "Scan Now"}</button></section>
    {error ? <div className="error-banner">{error}</div> : null}
    <div className="table-frame market-results-frame"><table className="market-results-table futures-results-table"><thead><tr>{futuresColumns.map(column => <th key={column.key}><button type="button" className="sortable-header" onClick={() => setSort(current => nextSort(current, column.key))}>{sortButtonLabel(sort, column.key, column.label)}</button></th>)}</tr></thead>
      <tbody>{results.map(r => <tr key={r.symbol} className={r.freshness === "STALE" ? "row-stale" : ""}><td className="futures-contract-cell"><div className="futures-contract-content"><strong>{r.symbol}</strong><span title={r.name}>{r.name}</span></div></td><td>{r.underlying}</td><td>{r.region ?? "-"}</td><td>{formatPrice(r.price)}</td><td className={tone(r.changeFromSettlement)}>{formatPercent(r.changeFromSettlement)}</td><td className={tone(r.changeFromOpen)}>{formatPercent(r.changeFromOpen)}</td><td className={tone(r.delta)}>{formatPercent(r.delta)}</td><td className={tone(r.rolling)}>{formatPercent(r.rolling)}</td><td><span className={`freshness freshness-${r.freshness.toLowerCase()}`}>{r.freshness}</span></td><td>{formatDateTime(r.providerTimestamp)}</td></tr>)}</tbody></table></div>
  </main>;
}
type FuturesColumnKey = "contract" | "underlying" | "region" | "price" | "settlement" | "open" | "delta" | "rolling" | "freshness" | "updated";
const futuresColumns: Array<SortColumn<FuturesResult, FuturesColumnKey>> = [
  { key: "contract", label: "Contract", value: (row) => row.symbol },
  { key: "underlying", label: "Underlying", value: (row) => row.underlying },
  { key: "region", label: "Region", value: (row) => row.region },
  { key: "price", label: "Price", value: (row) => row.price },
  { key: "settlement", label: "From Settlement", value: (row) => row.changeFromSettlement },
  { key: "open", label: "From Open", value: (row) => row.changeFromOpen },
  { key: "delta", label: "Delta", value: (row) => row.delta },
  { key: "rolling", label: "Rolling", value: (row) => row.rolling },
  { key: "freshness", label: "Freshness", value: (row) => row.freshness },
  { key: "updated", label: "Updated", value: (row) => row.providerTimestamp }
];
function FutureSummary({ title, result }: { title: string; result?: { symbol: string; name: string; changeFromSettlement: number } | null }) { return <div className="summary-panel"><span className="panel-title">{title}</span><strong>{result ? formatPercent(result.changeFromSettlement) : "-"}</strong><span className="panel-subtitle">{result ? `${result.symbol} · ${result.name}` : "No data"}</span></div>; }
function tone(value: number) { return value > 0 ? "value-positive" : value < 0 ? "value-negative" : "value-neutral"; }

"use client";

import { useEffect, useMemo, useState } from "react";
import { formatDateTime } from "@/lib/format";
import { ConnectionStatus } from "./ConnectionStatus";
import { MarketResultsTable } from "./MarketResultsTable";
import { SummaryStrip } from "./SummaryStrip";
import { useMarketDashboardSocket } from "../hooks/useMarketDashboardSocket";
import type { SortKey } from "../types/market-dashboard";
import { sortMarketResults } from "../utils/sortMarketResults";

export function DashboardPage() {
  const { snapshot, connectionState, error, fetchSnapshot, triggerScan } = useMarketDashboardSocket();
  const [sortKey, setSortKey] = useState<SortKey>("backend");
  const [query, setQuery] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);
  const [manualScanBusy, setManualScanBusy] = useState(false);

  useEffect(() => {
    fetchSnapshot().catch((err: unknown) => {
      setActionError(err instanceof Error ? err.message : "Could not load dashboard snapshot");
    });
  }, []);

  const results = useMemo(() => {
    const rawResults = snapshot?.results ?? [];
    const filtered = rawResults.filter((result) => {
      const normalizedQuery = query.trim().toLowerCase();
      if (!normalizedQuery) {
        return true;
      }

      return `${result.symbol} ${result.companyName}`.toLowerCase().includes(normalizedQuery);
    });

    return sortMarketResults(filtered, sortKey);
  }, [query, snapshot?.results, sortKey]);

  const manualScanEnabled = snapshot?.triggerMode === "BOTH" || snapshot?.triggerMode === "FRONTEND_TRIGGERED";

  const handleManualScan = async () => {
    setManualScanBusy(true);
    setActionError(null);
    try {
      await triggerScan();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "Could not trigger scan");
    } finally {
      setManualScanBusy(false);
    }
  };

  return (
    <main className="dashboard">
      <header className="dashboard-header">
        <div>
          <h1>Market Dashboard</h1>
          <p>Live watchlist movement ranked by backend scan results.</p>
        </div>
        <div className="header-status">
          <ConnectionStatus state={connectionState} error={error} />
          <span>Last scan: {formatDateTime(snapshot?.lastScanAt)}</span>
        </div>
      </header>

      <SummaryStrip snapshot={snapshot} />

      <section className="toolbar" aria-label="Dashboard controls">
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search symbol or company"
          aria-label="Search symbol or company"
        />
        <select value={sortKey} onChange={(event) => setSortKey(event.target.value as SortKey)} aria-label="Sort results">
          <option value="backend">Backend ranking</option>
          <option value="rolling">Rolling movement</option>
          <option value="delta">Delta movement</option>
          <option value="symbol">Symbol</option>
          <option value="updatedAt">Updated time</option>
        </select>
        <button type="button" onClick={() => fetchSnapshot()} className="secondary-button">
          Reload
        </button>
        {manualScanEnabled ? (
          <button type="button" onClick={handleManualScan} disabled={manualScanBusy}>
            {manualScanBusy ? "Scanning" : "Scan Now"}
          </button>
        ) : null}
      </section>

      {actionError ? <div className="error-banner">{actionError}</div> : null}

      <MarketResultsTable results={results} />
    </main>
  );
}

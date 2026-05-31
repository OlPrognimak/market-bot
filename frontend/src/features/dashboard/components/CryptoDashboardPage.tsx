"use client";

import { useEffect, useMemo, useState } from "react";
import { formatDateTime, formatPercent } from "@/lib/format";
import type { AppUser } from "@/features/users/types";
import { CryptoChartDialog } from "./CryptoChartDialog";
import { CryptoResultsTable } from "./CryptoResultsTable";
import { useCryptoDashboard } from "../hooks/useCryptoDashboard";
import type { CryptoScanResult, CryptoSortKey } from "../types/market-dashboard";
import { sortCryptoResults } from "../utils/sortCryptoResults";

type Props = {
  token: string;
  currentUser: AppUser;
  onBack: () => void;
  onLogout: () => void;
  onOpenSettings: () => void;
};

export function CryptoDashboardPage({ token, currentUser }: Props) {
  const { snapshot, fetchSnapshot, triggerScan } = useCryptoDashboard(token);
  const [query, setQuery] = useState("");
  const [sortKey, setSortKey] = useState<CryptoSortKey>("backend");
  const [directionFilter, setDirectionFilter] = useState("ALL");
  const [windowFilter, setWindowFilter] = useState("ALL");
  const [actionError, setActionError] = useState<string | null>(null);
  const [manualScanBusy, setManualScanBusy] = useState(false);
  const [chartResult, setChartResult] = useState<CryptoScanResult | null>(null);

  useEffect(() => {
    fetchSnapshot().catch((err: unknown) => {
      setActionError(err instanceof Error ? err.message : "Could not load crypto dashboard snapshot");
    });
  }, [fetchSnapshot]);

  const results = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    const filtered = (snapshot?.results ?? []).filter((result) => {
      if (directionFilter !== "ALL" && result.direction !== directionFilter) {
        return false;
      }
      if (windowFilter !== "ALL" && result.window !== windowFilter) {
        return false;
      }
      if (!normalizedQuery) {
        return true;
      }
      return [result.baseAsset, result.symbol, result.coinName]
        .join(" ")
        .toLowerCase()
        .includes(normalizedQuery);
    });

    return sortCryptoResults(filtered, sortKey);
  }, [directionFilter, query, snapshot?.results, sortKey, windowFilter]);

  const windows = useMemo(() => {
    return Array.from(new Set((snapshot?.results ?? []).map((result) => result.window))).sort();
  }, [snapshot?.results]);

  const manualScanEnabled = snapshot?.triggerMode === "BOTH" || snapshot?.triggerMode === "FRONTEND_TRIGGERED";

  const handleReload = async () => {
    setActionError(null);
    try {
      await fetchSnapshot();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "Could not reload crypto dashboard");
    }
  };

  const handleManualScan = async () => {
    setManualScanBusy(true);
    setActionError(null);
    try {
      await triggerScan();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "Could not trigger crypto scan");
    } finally {
      setManualScanBusy(false);
    }
  };

  return (
    <main className="dashboard">
      <header className="dashboard-header">
        <div>
          <h1>Crypto Dashboard</h1>
          <p>USDT pair movement from the crypto scanner.</p>
        </div>
        <div className="header-status">
          <span>Last scan: {formatDateTime(snapshot?.lastScanAt)}</span>
          <span>{currentUser.displayName} ({currentUser.role})</span>
        </div>
      </header>

      <section className="summary-strip crypto-summary" aria-label="Crypto scan summary">
        <div className="summary-panel">
          <span className="panel-title">Results</span>
          <strong>{snapshot?.results.length ?? 0}</strong>
          <span className="panel-subtitle">visible for this user</span>
        </div>
        <CryptoSummaryPanel title="Top rise" result={snapshot?.topPositive} positive />
        <CryptoSummaryPanel title="Top drop" result={snapshot?.topNegative} />
      </section>

      <section className="toolbar" aria-label="Crypto dashboard controls">
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search coin or pair"
          aria-label="Search coin or pair"
        />
        <select value={sortKey} onChange={(event) => setSortKey(event.target.value as CryptoSortKey)} aria-label="Sort crypto results">
          <option value="backend">Backend ranking</option>
          <option value="movement">Movement</option>
          <option value="volume">Volume</option>
          <option value="symbol">Coin</option>
          <option value="updatedAt">Updated time</option>
        </select>
        <select value={directionFilter} onChange={(event) => setDirectionFilter(event.target.value)} aria-label="Filter direction">
          <option value="ALL">All directions</option>
          <option value="UP">Up</option>
          <option value="DOWN">Down</option>
          <option value="NEUTRAL">Neutral</option>
        </select>
        <select value={windowFilter} onChange={(event) => setWindowFilter(event.target.value)} aria-label="Filter crypto window">
          <option value="ALL">All windows</option>
          {windows.map((window) => (
            <option key={window} value={window}>{window}</option>
          ))}
        </select>
        <button type="button" onClick={handleReload} className="secondary-button">
          Reload
        </button>
        {manualScanEnabled ? (
          <button type="button" onClick={handleManualScan} disabled={manualScanBusy}>
            {manualScanBusy ? "Scanning" : "Scan Now"}
          </button>
        ) : null}
      </section>

      {actionError ? <div className="error-banner">{actionError}</div> : null}

      <CryptoResultsTable results={results} onOpenChart={setChartResult} />
      <CryptoChartDialog token={token} result={chartResult} onClose={() => setChartResult(null)} />
    </main>
  );
}

function CryptoSummaryPanel({
  title,
  result,
  positive = false
}: {
  title: string;
  result?: { baseAsset: string; coinName: string; priceChangePercent: number } | null;
  positive?: boolean;
}) {
  return (
    <div className={`summary-panel ${positive ? "positive" : "negative"}`}>
      <span className="panel-title">{title}</span>
      <strong>{result ? formatPercent(result.priceChangePercent) : "-"}</strong>
      <span className="panel-subtitle">
        <span>{result ? `${result.coinName} (${result.baseAsset})` : "No data"}</span>
      </span>
    </div>
  );
}

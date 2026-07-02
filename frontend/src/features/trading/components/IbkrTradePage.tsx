"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { authFetch } from "@/lib/auth";
import { IbkrConnectionCard } from "./IbkrConnectionCard";
import { TradingPanel } from "./TradingPanel";
import type { MarketDashboardSnapshot, MarketScanResult } from "@/features/dashboard/types/market-dashboard";

type Props = {
  token: string;
};

export function IbkrTradePage({ token }: Props) {
  const [rows, setRows] = useState<MarketScanResult[]>([]);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadRows = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await authFetch(token, "/api/dashboard/snapshot");
      const snapshot = await response.json() as MarketDashboardSnapshot;
      setRows(snapshot.results ?? []);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load tradable shares");
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    loadRows();
  }, [loadRows]);

  const filteredRows = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    if (!normalizedQuery) {
      return rows;
    }
    return rows.filter((row) => [
      row.symbol,
      row.companyName,
      row.region,
      row.sector,
      row.exchange,
      row.currency
    ].filter(Boolean).join(" ").toLowerCase().includes(normalizedQuery));
  }, [query, rows]);

  return (
    <main className="dashboard">
      <header className="dashboard-header">
        <div>
          <h1>IBKR Trade</h1>
          <p>Manual trading workspace with paper execution first and IBKR connectivity planned behind explicit controls.</p>
        </div>
        <div className="header-status">
          <span>{rows.length} shares loaded</span>
        </div>
      </header>

      <section className="toolbar dashboard-toolbar" aria-label="IBKR trade controls">
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search symbol, company, sector, exchange"
          aria-label="Search tradable shares"
        />
        <button type="button" className="secondary-button" onClick={loadRows} disabled={loading}>
          {loading ? "Loading" : "Reload"}
        </button>
      </section>

      {error ? <div className="error-banner">{error}</div> : null}
      <IbkrConnectionCard token={token} />
      <TradingPanel token={token} rows={filteredRows} />
    </main>
  );
}

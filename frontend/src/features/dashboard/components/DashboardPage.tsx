"use client";

import { useEffect, useMemo, useState } from "react";
import { formatDateTime } from "@/lib/format";
import { ConnectionStatus } from "./ConnectionStatus";
import { MarketChartDialog } from "./MarketChartDialog";
import { MarketResultsTable } from "./MarketResultsTable";
import { SummaryStrip } from "./SummaryStrip";
import { useMarketDashboardSocket } from "../hooks/useMarketDashboardSocket";
import type { MarketScanResult, SortKey } from "../types/market-dashboard";
import { sortMarketResults } from "../utils/sortMarketResults";
import type { AppUser } from "@/features/users/types";

type Props = {
  token: string;
  currentUser: AppUser;
  onLogout: () => void;
  onOpenUsers: () => void;
  onOpenSettings: () => void;
  onOpenCrypto: () => void;
};

export function DashboardPage({ token, currentUser }: Props) {
  const { snapshot, connectionState, error, fetchSnapshot, triggerScan } = useMarketDashboardSocket(token);
  const [sortKey, setSortKey] = useState<SortKey>("backend");
  const [query, setQuery] = useState("");
  const [regionFilter, setRegionFilter] = useState("ALL");
  const [priorityFilter, setPriorityFilter] = useState("ALL");
  const [sectorFilter, setSectorFilter] = useState("ALL");
  const [exchangeFilter, setExchangeFilter] = useState("ALL");
  const [actionError, setActionError] = useState<string | null>(null);
  const [manualScanBusy, setManualScanBusy] = useState(false);
  const [chartSymbol, setChartSymbol] = useState<string | null>(null);

  useEffect(() => {
    fetchSnapshot().catch((err: unknown) => {
      setActionError(err instanceof Error ? err.message : "Could not load dashboard snapshot");
    });
  }, []);

  const results = useMemo(() => {
    const rawResults = snapshot?.results ?? [];
    const filtered = rawResults.filter((result) => {
      const normalizedQuery = normalizedFilterValue(query);
      if (regionFilter !== "ALL" && normalizedFilterValue(result.region) !== normalizedFilterValue(regionFilter)) {
        return false;
      }
      if (priorityFilter !== "ALL" && normalizedFilterValue(normalizedPriority(result.priority)) !== normalizedFilterValue(priorityFilter)) {
        return false;
      }
      if (sectorFilter !== "ALL" && normalizedFilterValue(result.sector) !== normalizedFilterValue(sectorFilter)) {
        return false;
      }
      if (exchangeFilter !== "ALL" && normalizedFilterValue(result.exchange) !== normalizedFilterValue(exchangeFilter)) {
        return false;
      }
      if (!normalizedQuery) {
        return true;
      }

      return [
        result.symbol,
        result.companyName,
        result.region,
        result.priority,
        result.sector,
        result.exchange,
        result.currency
      ].filter(Boolean).map(normalizedFilterValue).join(" ").includes(normalizedQuery);
    });

    return sortMarketResults(filtered, sortKey);
  }, [exchangeFilter, priorityFilter, query, regionFilter, sectorFilter, snapshot?.results, sortKey]);

  const filterOptions = useMemo(() => {
    const rawResults = snapshot?.results ?? [];

    return {
      regions: uniqueValues(rawResults.map((result) => result.region)),
      priorities: uniqueValues(rawResults.map((result) => normalizedPriority(result.priority))),
      sectors: uniqueValues(rawResults.map((result) => result.sector)),
      exchanges: uniqueValues(rawResults.map((result) => result.exchange))
    };
  }, [snapshot?.results]);

  useEffect(() => {
    if (regionFilter !== "ALL" && !hasFilterOption(filterOptions.regions, regionFilter)) {
      setRegionFilter("ALL");
    }
    if (priorityFilter !== "ALL" && !hasFilterOption(filterOptions.priorities, priorityFilter)) {
      setPriorityFilter("ALL");
    }
    if (sectorFilter !== "ALL" && !hasFilterOption(filterOptions.sectors, sectorFilter)) {
      setSectorFilter("ALL");
    }
    if (exchangeFilter !== "ALL" && !hasFilterOption(filterOptions.exchanges, exchangeFilter)) {
      setExchangeFilter("ALL");
    }
  }, [exchangeFilter, filterOptions, priorityFilter, regionFilter, sectorFilter]);

  const chartResult = useMemo(() => {
    if (!chartSymbol) {
      return null;
    }

    return snapshot?.results.find((result) => result.symbol === chartSymbol) ?? null;
  }, [chartSymbol, snapshot?.results]);

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
          <span>{currentUser.displayName} ({currentUser.role})</span>
        </div>
      </header>

      <SummaryStrip snapshot={snapshot} />

      <section className="toolbar" aria-label="Dashboard controls">
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search symbol, company, sector, exchange"
          aria-label="Search symbol, company, sector, exchange"
        />
        <select value={sortKey} onChange={(event) => setSortKey(event.target.value as SortKey)} aria-label="Sort results">
          <option value="backend">Backend ranking</option>
          <option value="rolling">Rolling movement</option>
          <option value="delta">Delta movement</option>
          <option value="symbol">Symbol</option>
          <option value="updatedAt">Updated time</option>
        </select>
        <select value={regionFilter} onChange={(event) => setRegionFilter(event.target.value)} aria-label="Filter country">
          <option value="ALL">All countries</option>
          {filterOptions.regions.map((region) => (
            <option key={region} value={region}>{region}</option>
          ))}
        </select>
        <select value={priorityFilter} onChange={(event) => setPriorityFilter(event.target.value)} aria-label="Filter priority">
          <option value="ALL">All priorities</option>
          {filterOptions.priorities.map((priority) => (
            <option key={priority} value={priority}>{priority}</option>
          ))}
        </select>
        <select value={sectorFilter} onChange={(event) => setSectorFilter(event.target.value)} aria-label="Filter sector">
          <option value="ALL">All sectors</option>
          {filterOptions.sectors.map((sector) => (
            <option key={sector} value={sector}>{sector}</option>
          ))}
        </select>
        <select value={exchangeFilter} onChange={(event) => setExchangeFilter(event.target.value)} aria-label="Filter exchange">
          <option value="ALL">All exchanges</option>
          {filterOptions.exchanges.map((exchange) => (
            <option key={exchange} value={exchange}>{exchange}</option>
          ))}
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

      <MarketResultsTable results={results} onOpenChart={(result) => setChartSymbol(result.symbol)} />
      <MarketChartDialog token={token} result={chartResult} onClose={() => setChartSymbol(null)} />
    </main>
  );
}

function uniqueValues(values: Array<string | null | undefined>): string[] {
  return Array.from(new Set(values.map(normalizedValue).filter((value) => value !== "-"))).sort();
}

function normalizedValue(value: string | null | undefined): string {
  return value && value.trim() ? value.trim() : "-";
}

function normalizedPriority(value: string | null | undefined): string {
  return value && value.trim() ? value.trim() : "NORMAL";
}

function normalizedFilterValue(value: string | null | undefined): string {
  return value ? value.trim().replace(/\s+/g, " ").toLowerCase() : "";
}

function hasFilterOption(options: string[], value: string): boolean {
  const normalizedValue = normalizedFilterValue(value);
  return options.some((option) => normalizedFilterValue(option) === normalizedValue);
}

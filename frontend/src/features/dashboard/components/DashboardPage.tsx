"use client";

import { useEffect, useMemo, useState } from "react";
import { formatDateTime } from "@/lib/format";
import { ALL_FILTER_VALUE } from "@/lib/filterConstants";
import { ConnectionStatus } from "./ConnectionStatus";
import { MarketChartDialog } from "./MarketChartDialog";
import { MarketResultsTable } from "./MarketResultsTable";
import { SummaryStrip } from "./SummaryStrip";
import { ExtendedHoursTable } from "./ExtendedHoursTable";
import { useMarketDashboardSocket } from "../hooks/useMarketDashboardSocket";
import { useExtendedHoursDashboard } from "../hooks/useExtendedHoursDashboard";
import type { SortKey } from "../types/market-dashboard";
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

const SESSION_VIEWS = ["OVERVIEW", "PRE_MARKET", "REGULAR", "POST_MARKET"] as const;
type SessionView = typeof SESSION_VIEWS[number];

export function DashboardPage({ token, currentUser }: Props) {
  const { snapshot, connectionState, error, fetchSnapshot, triggerScan } = useMarketDashboardSocket(token);
  const [sortKey, setSortKey] = useState<SortKey>("backend");
  const [query, setQuery] = useState("");
  const [regionFilter, setRegionFilter] = useState<string>(ALL_FILTER_VALUE);
  const [priorityFilter, setPriorityFilter] = useState<string>(ALL_FILTER_VALUE);
  const [sectorFilter, setSectorFilter] = useState<string>(ALL_FILTER_VALUE);
  const [exchangeFilter, setExchangeFilter] = useState<string>(ALL_FILTER_VALUE);
  const [actionError, setActionError] = useState<string | null>(null);
  const [manualScanBusy, setManualScanBusy] = useState(false);
  const [chartSymbol, setChartSymbol] = useState<string | null>(null);
  const sessionStorageKey = `market-dashboard-session:${currentUser.id}`;
  const [sessionView, setSessionView] = useState<SessionView>(() => {
    if (typeof window === "undefined") {
      return "REGULAR";
    }
    const stored = window.localStorage.getItem(sessionStorageKey);
    return SESSION_VIEWS.includes(stored as SessionView) ? stored as SessionView : "REGULAR";
  });
  const { snapshot: extendedSnapshot, fetchSnapshot: fetchExtendedSnapshot, triggerScan: triggerExtendedScan } = useExtendedHoursDashboard(token);

  useEffect(() => {
    fetchSnapshot().catch((err: unknown) => {
      setActionError(err instanceof Error ? err.message : "Could not load dashboard snapshot");
    });
  }, []);

  useEffect(() => {
    if (sessionView !== "REGULAR") {
      fetchExtendedSnapshot(sessionView === "OVERVIEW" ? undefined : sessionView).catch((err: unknown) => {
        setActionError(err instanceof Error ? err.message : "Could not load extended-hours snapshot");
      });
    }
  }, [fetchExtendedSnapshot, sessionView]);

  useEffect(() => {
    window.localStorage.setItem(sessionStorageKey, sessionView);
  }, [sessionStorageKey, sessionView]);

  const results = useMemo(() => {
    const rawResults = snapshot?.results ?? [];
    const filtered = rawResults.filter((result) => {
      const normalizedQuery = normalizedFilterValue(query);
      if (regionFilter !== ALL_FILTER_VALUE && normalizedFilterValue(result.region) !== normalizedFilterValue(regionFilter)) {
        return false;
      }
      if (priorityFilter !== ALL_FILTER_VALUE && normalizedFilterValue(normalizedPriority(result.priority)) !== normalizedFilterValue(priorityFilter)) {
        return false;
      }
      if (sectorFilter !== ALL_FILTER_VALUE && normalizedFilterValue(result.sector) !== normalizedFilterValue(sectorFilter)) {
        return false;
      }
      if (exchangeFilter !== ALL_FILTER_VALUE && normalizedFilterValue(result.exchange) !== normalizedFilterValue(exchangeFilter)) {
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
    if (regionFilter !== ALL_FILTER_VALUE && !hasFilterOption(filterOptions.regions, regionFilter)) {
      setRegionFilter(ALL_FILTER_VALUE);
    }
    if (priorityFilter !== ALL_FILTER_VALUE && !hasFilterOption(filterOptions.priorities, priorityFilter)) {
      setPriorityFilter(ALL_FILTER_VALUE);
    }
    if (sectorFilter !== ALL_FILTER_VALUE && !hasFilterOption(filterOptions.sectors, sectorFilter)) {
      setSectorFilter(ALL_FILTER_VALUE);
    }
    if (exchangeFilter !== ALL_FILTER_VALUE && !hasFilterOption(filterOptions.exchanges, exchangeFilter)) {
      setExchangeFilter(ALL_FILTER_VALUE);
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

      <div className="session-selector" role="tablist" aria-label="Trading session">
        {SESSION_VIEWS.map((session) => (
          <button key={session} type="button" className={sessionView === session ? "active" : ""} onClick={() => setSessionView(session)}>
            {session === "PRE_MARKET" ? "Pre-market" : session === "POST_MARKET" ? "Post-market" : session.charAt(0) + session.slice(1).toLowerCase()}
          </button>
        ))}
      </div>

      {sessionView === "REGULAR" ? <section className="toolbar dashboard-toolbar" aria-label="Dashboard controls">
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
          <option value={ALL_FILTER_VALUE}>All countries</option>
          {filterOptions.regions.map((region) => (
            <option key={region} value={region}>{region}</option>
          ))}
        </select>
        <select value={priorityFilter} onChange={(event) => setPriorityFilter(event.target.value)} aria-label="Filter priority">
          <option value={ALL_FILTER_VALUE}>All priorities</option>
          {filterOptions.priorities.map((priority) => (
            <option key={priority} value={priority}>{priority}</option>
          ))}
        </select>
        <select value={sectorFilter} onChange={(event) => setSectorFilter(event.target.value)} aria-label="Filter sector">
          <option value={ALL_FILTER_VALUE}>All sectors</option>
          {filterOptions.sectors.map((sector) => (
            <option key={sector} value={sector}>{sector}</option>
          ))}
        </select>
        <select value={exchangeFilter} onChange={(event) => setExchangeFilter(event.target.value)} aria-label="Filter exchange">
          <option value={ALL_FILTER_VALUE}>All exchanges</option>
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
      </section> : <section className="toolbar" aria-label="Extended-hours controls">
        <span className="toolbar-note">Yahoo extended-hours data may be delayed or unavailable outside US markets.</span>
        <button type="button" className="secondary-button" onClick={() => fetchExtendedSnapshot(sessionView === "OVERVIEW" ? undefined : sessionView)}>Reload</button>
        <button type="button" onClick={() => triggerExtendedScan(sessionView === "OVERVIEW" ? undefined : sessionView)}>Scan Now</button>
      </section>}

      {actionError ? <div className="error-banner">{actionError}</div> : null}

      {sessionView === "REGULAR"
        ? <MarketResultsTable results={results} onOpenChart={(result) => setChartSymbol(result.symbol)} />
        : <ExtendedHoursTable results={extendedSnapshot?.results ?? []} />}
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

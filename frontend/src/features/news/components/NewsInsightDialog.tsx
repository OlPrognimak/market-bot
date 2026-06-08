"use client";

import { useEffect, useMemo, useState } from "react";
import { fetchNewsInsights, fetchNewsRefreshStatus, refreshNewsInsights } from "../api";
import type { InstrumentType, NewsInsight, NewsDirection } from "../types";

type Props = {
  token: string;
  instrumentType: InstrumentType;
  symbol: string;
  instrumentName: string;
  onClose: () => void;
};

type Filter = "ALL" | "UP" | "DOWN" | "HIGH_IMPACT";

export function NewsInsightDialog({ token, instrumentType, symbol, instrumentName, onClose }: Props) {
  const [insights, setInsights] = useState<NewsInsight[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [filter, setFilter] = useState<Filter>("ALL");
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [maximized, setMaximized] = useState(false);

  async function load(refresh: boolean) {
    setError(null);
    refresh ? setRefreshing(true) : setLoading(true);
    try {
      const loaded = refresh
        ? (await startAndWaitForRefresh()).insights
        : await fetchNewsInsights(token, instrumentType, symbol);
      setInsights(loaded);
      setSelectedId((current) => current && loaded.some((item) => item.id === current) ? current : loaded[0]?.id ?? null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load news");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }

  async function startAndWaitForRefresh() {
    let status = await refreshNewsInsights(token, instrumentType, symbol);
    setInsights(status.insights);
    while (status.refreshing) {
      await sleep(1500);
      status = await fetchNewsRefreshStatus(token, instrumentType, symbol);
      setInsights(status.insights);
      setSelectedId((current) => current ?? status.insights[0]?.id ?? null);
    }
    return status;
  }

  useEffect(() => {
    let cancelled = false;
    async function initialLoad() {
      setLoading(true);
      try {
        const saved = await fetchNewsInsights(token, instrumentType, symbol);
        if (!cancelled) {
          setInsights(saved);
          setSelectedId(saved[0]?.id ?? null);
        }
        const refreshed = await startAndWaitForRefresh();
        if (!cancelled) {
          setInsights(refreshed.insights);
          setSelectedId((current) => current ?? refreshed.insights[0]?.id ?? null);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Could not load news");
          try {
            const saved = await fetchNewsInsights(token, instrumentType, symbol);
            if (!cancelled) {
              setInsights(saved);
              setSelectedId((current) => current ?? saved[0]?.id ?? null);
            }
          } catch {
            // The original request error remains visible.
          }
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }
    initialLoad();
    return () => {
      cancelled = true;
    };
  }, [instrumentType, symbol, token]);

  const filtered = useMemo(() => insights.filter((item) => {
    if (filter === "ALL") return true;
    if (filter === "HIGH_IMPACT") return item.impactScore >= 70;
    return item.direction === filter;
  }), [filter, insights]);
  const selected = insights.find((item) => item.id === selectedId) ?? filtered[0] ?? null;

  return (
    <div className="modal-backdrop news-modal-backdrop" role="presentation" onClick={onClose}>
      <section className={`news-dialog${maximized ? " maximized" : ""}`} role="dialog" aria-modal="true" aria-labelledby="news-title" onClick={(event) => event.stopPropagation()}>
        <header className="chart-dialog-header">
          <div>
            <h2 id="news-title">News: {symbol} - {instrumentName}</h2>
            <p>Current provider articles with AI market-impact analysis</p>
          </div>
          <div className="chart-dialog-actions">
            <button type="button" className="secondary-button" onClick={() => load(true)} disabled={refreshing}>
              {refreshing ? "Refreshing" : "Refresh"}
            </button>
            <button
              type="button"
              className="icon-button dialog-size-button"
              onClick={() => setMaximized((current) => !current)}
              aria-label={maximized ? "Restore news dialog" : "Maximize news dialog"}
              title={maximized ? "Restore" : "Maximize"}
            >
              <span className={maximized ? "restore-dialog-icon" : "maximize-dialog-icon"} aria-hidden="true" />
            </button>
            <button type="button" className="icon-button" onClick={onClose} aria-label="Close news">x</button>
          </div>
        </header>

        <div className="range-tabs" role="tablist" aria-label="News filter">
          {(["ALL", "UP", "DOWN", "HIGH_IMPACT"] as Filter[]).map((item) => (
            <button key={item} type="button" className={filter === item ? "active" : ""} onClick={() => setFilter(item)}>
              {filterLabel(item)}
            </button>
          ))}
        </div>

        {error ? <p className="news-error">{error}</p> : null}
        {loading ? <div className="news-empty">Loading saved and current news</div> : null}
        {!loading && filtered.length === 0 ? <div className="news-empty">No news is available for this instrument and filter.</div> : null}

        {!loading && filtered.length > 0 ? (
          <div className="news-browser">
            <div className="news-list">
              {filtered.map((item) => (
                <button key={item.id} type="button" className={item.id === selected?.id ? "selected" : ""} onClick={() => setSelectedId(item.id)}>
                  <span className={`news-direction ${directionClass(item.direction)}`}>{directionLabel(item.direction)}</span>
                  <strong>{item.title}</strong>
                  <small>{item.sourceName ?? "Source"} | {formatDate(item.publishedAt)}</small>
                  <small>Impact {item.impactScore} | Confidence {item.confidence}</small>
                </button>
              ))}
            </div>

            {selected ? (
              <article className="news-detail">
                <div className={`ai-analysis-status ${analysisStatus(selected).className}`}>
                  <strong>{analysisStatus(selected).label}</strong>
                  <span>{analysisStatus(selected).detail}</span>
                </div>
                <header>
                  <span className={`news-direction ${directionClass(selected.direction)}`}>{directionLabel(selected.direction)}</span>
                  <h3>{selected.title}</h3>
                  <p>{selected.sourceName ?? "Source"} | {formatDate(selected.publishedAt)}</p>
                </header>
                <dl className="news-metrics">
                  <div><dt>Impact</dt><dd>{selected.impactScore}</dd></div>
                  <div><dt>Confidence</dt><dd>{selected.confidence}</dd></div>
                  <div><dt>Horizon</dt><dd>{selected.timeHorizon}</dd></div>
                </dl>
                <h4>AI summary</h4>
                <p>{selected.summary}</p>
                <h4>Reason</h4>
                <p>{selected.reason}</p>
                {selected.articleSummary ? <><h4>Provider summary</h4><p>{selected.articleSummary}</p></> : null}
                <a className="primary-link" href={selected.sourceUrl} target="_blank" rel="noopener noreferrer">Open original article</a>
              </article>
            ) : null}
          </div>
        ) : null}
      </section>
    </div>
  );
}

function filterLabel(filter: Filter): string {
  if (filter === "HIGH_IMPACT") return "High impact";
  if (filter === "ALL") return "All";
  return filter === "UP" ? "Positive" : "Negative";
}

function directionLabel(direction: NewsDirection): string {
  return direction === "UP" ? "Positive" : direction === "DOWN" ? "Negative" : direction.charAt(0) + direction.slice(1).toLowerCase();
}

function directionClass(direction: NewsDirection): string {
  return direction === "UP" ? "positive" : direction === "DOWN" ? "negative" : "neutral";
}

function analysisStatus(insight: NewsInsight): { className: "available" | "unavailable"; label: string; detail: string } {
  const reason = insight.reason.toLowerCase();
  const unavailable = reason.startsWith("ai analysis unavailable")
    || reason.startsWith("ai analysis disabled")
    || reason.startsWith("ai analysis failed")
    || reason.startsWith("ai analysis temporarily unavailable");
  if (unavailable) {
    return { className: "unavailable", label: "AI analysis unavailable", detail: insight.reason };
  }
  if (insight.direction === "UNKNOWN") {
    return {
      className: "available",
      label: "AI analysis completed: direction inconclusive",
      detail: `Analyzed ${formatDate(insight.analyzedAt)}`
    };
  }
  return { className: "available", label: "AI analysis completed", detail: `Analyzed ${formatDate(insight.analyzedAt)}` };
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("en-US", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function sleep(milliseconds: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
}

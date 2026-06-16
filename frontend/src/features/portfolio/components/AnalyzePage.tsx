"use client";

import { useEffect, useState } from "react";
import { fetchPortfolioAnalysis } from "../api";
import type { PortfolioAnalysis } from "../types";

const number = (value: number | null | undefined, digits = 2) =>
  value == null ? "-" : value.toLocaleString(undefined, { maximumFractionDigits: digits });

const money = (value: number | null | undefined, currency: string) =>
  value == null ? "-" : new Intl.NumberFormat(undefined, { style: "currency", currency }).format(value);

export function AnalyzePage({ token }: { token: string }) {
  const [analysis, setAnalysis] = useState<PortfolioAnalysis | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [ticker, setTicker] = useState("");
  const [providerType, setProviderType] = useState("ALL");
  const [appliedFilters, setAppliedFilters] = useState<{ from?: string; to?: string; ticker?: string; providerType?: string }>({});

  useEffect(() => {
    setLoading(true);
    setError(null);
    fetchPortfolioAnalysis(token, appliedFilters)
      .then(setAnalysis)
      .catch((reason) => setError(reason instanceof Error ? reason.message : "Could not load analysis"))
      .finally(() => setLoading(false));
  }, [appliedFilters, token]);

  if (error) return <main className="dashboard"><div className="error-banner">{error}</div></main>;
  if (!analysis) return <main className="dashboard">Loading portfolio analysis</main>;

  return (
    <main className="dashboard">
      <header className="dashboard-header">
        <div>
          <h1>Analyze</h1>
          <p>Revolut portfolio activity combined with the latest matching market-bot quotes.</p>
        </div>
      </header>

      <section className="portfolio-analysis-filters">
        <label>
          From
          <input type="date" value={from} max={to || undefined} onChange={(event) => setFrom(event.target.value)} />
        </label>
        <label>
          To
          <input type="date" value={to} min={from || undefined} onChange={(event) => setTo(event.target.value)} />
        </label>
        <label>
          Ticker
          <select value={ticker} onChange={(event) => setTicker(event.target.value)}>
            <option value="">All tickers</option>
            {analysis.availableTickers.map((item) => <option key={item} value={item}>{item}</option>)}
          </select>
        </label>
        <label>
          Provider
          <select value={providerType} onChange={(event) => setProviderType(event.target.value)}>
            <option value="ALL">All providers</option>
            <option value="REVOLUT">Revolut</option>
            <option value="TRADE_REPUBLIC">Trade Republic</option>
          </select>
        </label>
        <button type="button" disabled={loading || Boolean(from && to && from > to)} onClick={() => setAppliedFilters({ from, to, ticker, providerType })}>
          {loading ? "Calculating" : "Apply filters"}
        </button>
        <button type="button" className="secondary-button" disabled={loading} onClick={() => {
          setFrom("");
          setTo("");
          setTicker("");
          setProviderType("ALL");
          setAppliedFilters({});
        }}>
          Clear
        </button>
      </section>

      <section className="portfolio-summary-grid">
        <Summary title="Provider" value={analysis.selectedProviderType ?? "ALL"} />
        <Summary title="Filtered transactions" value={number(analysis.transactionCount, 0)} />
        <Summary title="Filtered realized lots" value={number(analysis.realizedLotCount, 0)} />
        <Summary title="Filtered income records" value={number(analysis.incomeCount, 0)} />
        <Summary title="Open positions" value={number(analysis.positions.length, 0)} />
      </section>

      <section className="portfolio-currency-summary">
        <CurrencySummary title="Realized wins" values={analysis.realizedProfitByCurrency} />
        <CurrencySummary title="Realized losses" values={analysis.realizedLossByCurrency} />
        <CurrencySummary title="Net realized P/L" values={analysis.realizedPnlByCurrency} />
        <CurrencySummary title="Net income" values={analysis.incomeByCurrency} />
      </section>

      <h2>Filtered ticker results</h2>
      <div className="table-frame portfolio-analysis-table-frame">
        <table className="portfolio-table filtered-results-table">
          <thead><tr><th>Ticker</th><th>Provider</th><th>Currency</th><th>Transactions</th><th>Realized lots</th><th>Income records</th><th>Realized wins</th><th>Realized losses</th><th>Net realized P/L</th><th>Net income</th></tr></thead>
          <tbody>
            {analysis.filteredTickerResults.length ? analysis.filteredTickerResults.map((result) => (
              <tr key={`${result.providerType}-${result.ticker}-${result.currency}`}>
                <td>{result.ticker}</td>
                <td>{result.providerType}</td>
                <td>{result.currency}</td>
                <td>{number(result.transactionCount, 0)}</td>
                <td>{number(result.realizedLotCount, 0)}</td>
                <td>{number(result.incomeCount, 0)}</td>
                <td>{money(result.realizedProfit, result.currency)}</td>
                <td>{money(result.realizedLoss, result.currency)}</td>
                <td>{money(result.realizedPnl, result.currency)}</td>
                <td>{money(result.income, result.currency)}</td>
              </tr>
            )) : (
              <tr><td colSpan={10}>No tickers matched the selected filter.</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {analysis.selectedTicker && (
        <>
          <h2>{analysis.selectedTicker} realized buy/sell details</h2>
          <p className="portfolio-analysis-note">
            Each row is one Revolut realized lot: shares acquired by a buy and later closed by a sell.
            One sell can produce multiple rows when it closes shares from multiple buys. Trade Republic tax overview
            rows provide sell-level P/L without the original acquisition date.
          </p>
          <div className="table-frame portfolio-analysis-table-frame">
            <table className="portfolio-table realized-lots-table">
              <thead><tr><th>Acquired date</th><th>Sell date</th><th>Ticker</th><th>Provider</th><th>Currency</th><th>Quantity</th><th>Cost basis</th><th>Sell proceeds</th><th>Realized wins</th><th>Realized losses</th><th>Net realized P/L</th></tr></thead>
              <tbody>
                {analysis.selectedTickerRealizedLots.length ? analysis.selectedTickerRealizedLots.map((lot, index) => (
                  <tr key={`${lot.acquiredDate}-${lot.soldDate}-${lot.ticker}-${lot.quantity}-${index}`}>
                    <td>{lot.acquiredDate}</td>
                    <td>{lot.soldDate}</td>
                    <td>{lot.ticker}</td>
                    <td>{lot.providerType}</td>
                    <td>{lot.currency}</td>
                    <td>{number(lot.quantity, 8)}</td>
                    <td>{money(lot.costBasis, lot.currency)}</td>
                    <td>{money(lot.grossProceeds, lot.currency)}</td>
                    <td>{money(lot.realizedProfit, lot.currency)}</td>
                    <td>{money(lot.realizedLoss, lot.currency)}</td>
                    <td>{money(lot.realizedPnl, lot.currency)}</td>
                  </tr>
                )) : (
                  <tr><td colSpan={11}>No realized sales matched the selected ticker and period.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </>
      )}

      <h2>Current positions</h2>
      <div className="table-frame">
        <table className="portfolio-table positions-table">
          <thead><tr><th>Ticker</th><th>Provider</th><th>Currency</th><th>Quantity</th><th>Average cost</th><th>Cost basis</th><th>Market price</th><th>Market value</th><th>Unrealized P/L</th><th>P/L %</th><th>Status</th></tr></thead>
          <tbody>
            {analysis.positions.map((position) => (
              <tr key={`${position.providerType}-${position.ticker}-${position.currency}`} className={position.reconciliationStatus.startsWith("UNRECONCILED") ? "warning-row" : ""}>
                <td>{position.ticker}</td>
                <td>{position.providerType}</td>
                <td>{position.currency}</td>
                <td>{number(position.quantity, 8)}</td>
                <td>{money(position.averageCost, position.currency)}</td>
                <td>{money(position.remainingCostBasis, position.currency)}</td>
                <td>{money(position.currentPrice, position.currency)}</td>
                <td>{money(position.marketValue, position.currency)}</td>
                <td>{money(position.unrealizedPnl, position.currency)}</td>
                <td>{position.unrealizedPnlPercent == null ? "-" : `${number(position.unrealizedPnlPercent)}%`}</td>
                <td>{position.reconciliationStatus.replaceAll("_", " ")}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </main>
  );
}

function Summary({ title, value }: { title: string; value: string }) {
  return <div className="summary-panel"><span className="panel-title">{title}</span><strong>{value}</strong></div>;
}

function CurrencySummary({ title, values }: { title: string; values: Record<string, number> }) {
  const entries = Object.entries(values);
  return (
    <div className="summary-panel">
      <span className="panel-title">{title}</span>
      {entries.length ? entries.map(([currency, value]) => <strong key={currency}>{money(value, currency)}</strong>) : <strong>-</strong>}
    </div>
  );
}

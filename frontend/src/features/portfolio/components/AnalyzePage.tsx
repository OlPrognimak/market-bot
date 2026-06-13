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

  useEffect(() => {
    fetchPortfolioAnalysis(token)
      .then(setAnalysis)
      .catch((reason) => setError(reason instanceof Error ? reason.message : "Could not load analysis"));
  }, [token]);

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

      <section className="portfolio-summary-grid">
        <Summary title="Provider" value={analysis.providerType} />
        <Summary title="Transactions" value={number(analysis.transactionCount, 0)} />
        <Summary title="Realized lots" value={number(analysis.realizedLotCount, 0)} />
        <Summary title="Income records" value={number(analysis.incomeCount, 0)} />
        <Summary title="Open positions" value={number(analysis.positions.length, 0)} />
      </section>

      <section className="portfolio-currency-summary">
        <div className="summary-panel">
          <span className="panel-title">Realized P/L</span>
          {Object.entries(analysis.realizedPnlByCurrency).map(([currency, value]) => <strong key={currency}>{money(value, currency)}</strong>)}
        </div>
        <div className="summary-panel">
          <span className="panel-title">Net income</span>
          {Object.entries(analysis.incomeByCurrency).map(([currency, value]) => <strong key={currency}>{money(value, currency)}</strong>)}
        </div>
      </section>

      <h2>Current positions</h2>
      <div className="table-frame">
        <table className="portfolio-table positions-table">
          <thead><tr><th>Ticker</th><th>Provider</th><th>Currency</th><th>Quantity</th><th>Average cost</th><th>Cost basis</th><th>Market price</th><th>Market value</th><th>Unrealized P/L</th><th>P/L %</th><th>Status</th></tr></thead>
          <tbody>
            {analysis.positions.map((position) => (
              <tr key={`${position.ticker}-${position.currency}`} className={position.reconciliationStatus.startsWith("UNRECONCILED") ? "warning-row" : ""}>
                <td>{position.ticker}</td>
                <td>{analysis.providerType}</td>
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

      <h2>Recent activity</h2>
      <div className="table-frame">
        <table className="portfolio-table">
          <thead><tr><th>Time</th><th>Provider</th><th>Ticker</th><th>Type</th><th>Quantity</th><th>Price</th><th>Total</th></tr></thead>
          <tbody>
            {analysis.recentTransactions.map((item, index) => (
              <tr key={`${item.eventTime}-${index}`}>
                <td>{new Date(item.eventTime).toLocaleString()}</td>
                <td>{analysis.providerType}</td>
                <td>{item.ticker ?? "-"}</td>
                <td>{item.transactionType}</td>
                <td>{number(item.quantity, 8)}</td>
                <td>{money(item.pricePerShare, item.currency)}</td>
                <td>{money(item.totalAmount, item.currency)}</td>
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

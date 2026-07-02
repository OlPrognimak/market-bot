"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { fetchTradingPositions, fetchTradingStatus, previewTradeOrder, submitTradeOrder } from "../api";
import type { TradeOrderPreview, TradeOrderRequest, TradeOrderResult, TradingPosition, TradingStatus } from "../types";
import type { MarketScanResult } from "@/features/dashboard/types/market-dashboard";

type Props = {
  token: string;
  rows: MarketScanResult[];
};

export function TradingPanel({ token, rows }: Props) {
  const [status, setStatus] = useState<TradingStatus | null>(null);
  const [positions, setPositions] = useState<TradingPosition[]>([]);
  const [preview, setPreview] = useState<TradeOrderPreview | null>(null);
  const [draft, setDraft] = useState<TradeOrderRequest | null>(null);
  const [lastResult, setLastResult] = useState<TradeOrderResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadTradingState = useCallback(async () => {
    const nextStatus = await fetchTradingStatus(token);
    setStatus(nextStatus);
    if (nextStatus.enabled) {
      setPositions(await fetchTradingPositions(token));
    }
  }, [token]);

  useEffect(() => {
    loadTradingState().catch((err: unknown) => {
      setError(err instanceof Error ? err.message : "Could not load trading state");
    });
  }, [loadTradingState]);

  const positionsBySymbol = useMemo(() => new Map(positions.map((position) => [position.symbol, position])), [positions]);

  const openPreview = async (payload: TradeOrderRequest) => {
    setBusy(true);
    setError(null);
    setLastResult(null);
    try {
      const nextPreview = await previewTradeOrder(token, payload);
      setDraft(payload);
      setPreview(nextPreview);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not preview order");
    } finally {
      setBusy(false);
    }
  };

  const submitPreview = async () => {
    if (!preview || !draft) return;
    setBusy(true);
    setError(null);
    try {
      const result = await submitTradeOrder(token, {
        ...draft,
        previewId: preview.previewId,
        confirmationToken: preview.confirmationToken
      });
      setLastResult(result);
      setPreview(null);
      setDraft(null);
      setPositions(await fetchTradingPositions(token));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not submit order");
    } finally {
      setBusy(false);
    }
  };

  if (!status || !status.enabled) {
    return null;
  }

  return (
    <section className="trading-panel" aria-label="Trading">
      <div className="trading-panel-header">
        <div>
          <h2>Trading</h2>
          <span>{status.mode} / {status.brokerType}</span>
        </div>
        <button type="button" className="secondary-button" onClick={() => loadTradingState()} disabled={busy}>Reload</button>
      </div>
      {status.mode === "LIVE" ? <div className="error-banner">Live broker mode is active.</div> : null}
      {error ? <div className="error-banner">{error}</div> : null}
      {lastResult ? <div className="success-banner">{lastResult.symbol} {lastResult.status}: {lastResult.message ?? "-"}</div> : null}
      <div className="trading-actions-grid">
        {rows.slice(0, 30).map((row) => {
          const position = positionsBySymbol.get(row.symbol);
          return (
            <div className="trading-row" key={row.symbol}>
              <button type="button" className="symbol-link" onClick={() => openPreview(buyAmount(row, 100))}>{row.symbol}</button>
              <span>{position ? `${position.quantity.toFixed(4)} @ ${position.avgPrice.toFixed(2)}` : "-"}</span>
              <button type="button" onClick={() => openPreview(buyAmount(row, 100))} disabled={busy}>Buy 100</button>
              <button type="button" onClick={() => openPreview(buyAmount(row, 250))} disabled={busy}>Buy 250</button>
              <button type="button" className="secondary-button" onClick={() => position && openPreview(sellQuantity(row, position.quantity / 2))} disabled={busy || !position}>Sell 50%</button>
              <button type="button" className="secondary-button" onClick={() => position && openPreview(sellQuantity(row, position.quantity))} disabled={busy || !position}>Sell All</button>
            </div>
          );
        })}
      </div>
      {positions.length > 0 ? (
        <div className="positions-strip">
          {positions.map((position) => (
            <span key={position.symbol}>
              {position.symbol}: {position.quantity.toFixed(4)} / P&amp;L {position.unrealizedPnl.toFixed(2)} {position.currency}
            </span>
          ))}
        </div>
      ) : null}
      {preview ? (
        <div className="modal-backdrop" role="presentation">
          <div className="trade-modal" role="dialog" aria-modal="true" aria-label="Order preview">
            <header>
              <h2>{preview.tradingMode} {preview.side} {preview.symbol}</h2>
              <button type="button" className="secondary-button" onClick={() => setPreview(null)}>Close</button>
            </header>
            <dl className="trade-preview-grid">
              <dt>Price</dt><dd>{preview.currentPrice.toFixed(2)} {preview.currency}</dd>
              <dt>Quantity</dt><dd>{preview.estimatedQuantity.toFixed(8)}</dd>
              <dt>Value</dt><dd>{preview.estimatedValue.toFixed(2)} {preview.currency}</dd>
              <dt>Type</dt><dd>{preview.orderType}</dd>
              <dt>Expires</dt><dd>{new Date(preview.expiresAt).toLocaleTimeString()}</dd>
            </dl>
            {preview.warnings.length > 0 ? <div className="warning-list">{formatMessages(preview.warnings).join(", ")}</div> : null}
            {preview.rejectionReasons.length > 0 ? <div className="error-banner">{formatMessages(preview.rejectionReasons).join(", ")}</div> : null}
            <footer>
              <button type="button" className="secondary-button" onClick={() => setPreview(null)}>Cancel</button>
              <button type="button" onClick={submitPreview} disabled={busy || !preview.allowed}>Confirm</button>
            </footer>
          </div>
        </div>
      ) : null}
    </section>
  );
}

function buyAmount(row: MarketScanResult, amount: number): TradeOrderRequest {
  return {
    symbol: row.symbol,
    side: "BUY",
    amount,
    orderType: "MARKET",
    currency: row.currency ?? "USD",
    exchange: row.exchange
  };
}

function sellQuantity(row: MarketScanResult, quantity: number): TradeOrderRequest {
  return {
    symbol: row.symbol,
    side: "SELL",
    quantity,
    orderType: "MARKET",
    currency: row.currency ?? "USD",
    exchange: row.exchange
  };
}

const messageLabels: Record<string, string> = {
  CONFIRMATION_REQUIRED: "Confirmation is required.",
  DUPLICATE_PENDING_ORDER: "There is already a pending order for this symbol.",
  LIVE_TRADING_DISABLED: "Live trading is disabled.",
  NO_CURRENT_QUOTE: "No current quote is available.",
  ORDER_VALUE_TOO_HIGH: "The order value is above the configured limit.",
  PAPER_MARKET_ORDER_EXECUTES_AT_LATEST_QUOTE: "Paper market orders execute at the latest quote.",
  PREVIEW_EXPIRED: "The preview has expired.",
  QUANTITY_AND_AMOUNT_CONFLICT: "Use either quantity or amount, not both.",
  QUANTITY_OR_AMOUNT_REQUIRED: "Enter a quantity or amount.",
  SELL_QUANTITY_EXCEEDS_POSITION: "Sell quantity is larger than the current paper position.",
  SYMBOL_NOT_IN_WATCHLIST: "This symbol is not in your watchlist.",
  TRADING_DISABLED: "Trading is disabled.",
  UNSUPPORTED_ORDER_TYPE: "This order type is not supported."
};

function formatMessages(messages: string[]): string[] {
  return messages.map((message) => messageLabels[message] ?? toSentence(message));
}

function toSentence(value: string): string {
  const normalized = value.toLowerCase().replace(/_/g, " ");
  return normalized.charAt(0).toUpperCase() + normalized.slice(1) + ".";
}

"use client";

import { useCallback, useEffect, useState } from "react";
import { connectIbkr, disconnectIbkr, fetchIbkrStatus } from "../api";
import type { IbkrConnectionStatus } from "../types";

type Props = {
  token: string;
};

export function IbkrConnectionCard({ token }: Props) {
  const [status, setStatus] = useState<IbkrConnectionStatus | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadStatus = useCallback(async () => {
    setError(null);
    setStatus(await fetchIbkrStatus(token));
  }, [token]);

  useEffect(() => {
    loadStatus().catch((err: unknown) => {
      setError(err instanceof Error ? err.message : "Could not load IBKR status");
    });
  }, [loadStatus]);

  const run = async (action: "connect" | "disconnect") => {
    setBusy(true);
    setError(null);
    try {
      setStatus(action === "connect" ? await connectIbkr(token) : await disconnectIbkr(token));
    } catch (err) {
      setError(err instanceof Error ? err.message : `Could not ${action} IBKR`);
    } finally {
      setBusy(false);
    }
  };

  if (!status) {
    return <section className="ibkr-connection-card">Loading IBKR connection</section>;
  }

  return (
    <section className="ibkr-connection-card" aria-label="IBKR connection">
      <div className="ibkr-connection-header">
        <div>
          <h2>IBKR Connection</h2>
          <span className={status.connected ? "connection-pill connected" : "connection-pill"}>
            {status.connected ? "Connected" : status.state}
          </span>
        </div>
        <div className="ibkr-connection-actions">
          <button type="button" className="secondary-button" onClick={loadStatus} disabled={busy}>Refresh</button>
          <button type="button" onClick={() => run("connect")} disabled={busy || status.connected || !status.enabled}>Connect</button>
          <button type="button" className="secondary-button" onClick={() => run("disconnect")} disabled={busy || !status.connected}>Disconnect</button>
        </div>
      </div>
      <dl className="ibkr-connection-grid">
        <dt>Host</dt><dd>{status.host}</dd>
        <dt>Port</dt><dd>{status.port}</dd>
        <dt>Client ID</dt><dd>{status.clientId}</dd>
        <dt>Account</dt><dd>{status.accountId || "-"}</dd>
        <dt>Paper only</dt><dd>{status.paperAccountOnly ? "Yes" : "No"}</dd>
        <dt>Message</dt><dd>{status.message}</dd>
      </dl>
      {error ? <div className="error-banner">{error}</div> : null}
      {!status.enabled ? <div className="warning-list">Enable IBKR with MARKET_BOT_TRADING_IBKR_ENABLED=true.</div> : null}
    </section>
  );
}

"use client";

import { useEffect, useMemo, useState } from "react";
import { backendHttpUrl, backendWsUrl } from "@/lib/websocket";
import type { MarketDashboardSnapshot } from "../types/market-dashboard";

type ConnectionState = "connecting" | "connected" | "reconnecting" | "disconnected";

export function useMarketDashboardSocket() {
  const [snapshot, setSnapshot] = useState<MarketDashboardSnapshot | null>(null);
  const [connectionState, setConnectionState] = useState<ConnectionState>("connecting");
  const [error, setError] = useState<string | null>(null);
  const wsUrl = useMemo(() => backendWsUrl(), []);

  useEffect(() => {
    let socket: WebSocket | null = null;
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    let manuallyClosed = false;

    const connect = () => {
      setConnectionState((state) => (state === "disconnected" ? "reconnecting" : "connecting"));
      socket = new WebSocket(wsUrl);

      socket.onopen = () => {
        setConnectionState("connected");
        setError(null);
      };

      socket.onmessage = (event) => {
        setSnapshot(JSON.parse(event.data) as MarketDashboardSnapshot);
      };

      socket.onerror = () => {
        setError("WebSocket connection failed");
      };

      socket.onclose = () => {
        if (manuallyClosed) {
          return;
        }

        setConnectionState("disconnected");
        reconnectTimer = setTimeout(connect, 2500);
      };
    };

    connect();

    return () => {
      manuallyClosed = true;
      if (reconnectTimer) {
        clearTimeout(reconnectTimer);
      }
      socket?.close();
    };
  }, [wsUrl]);

  const fetchSnapshot = async () => {
    const response = await fetch(`${backendHttpUrl()}/api/dashboard/snapshot`);
    if (!response.ok) {
      throw new Error(`Snapshot request failed with ${response.status}`);
    }
    setSnapshot((await response.json()) as MarketDashboardSnapshot);
  };

  const triggerScan = async () => {
    const response = await fetch(`${backendHttpUrl()}/api/dashboard/scan`, {
      method: "POST"
    });
    if (!response.ok) {
      throw new Error(`Scan request failed with ${response.status}`);
    }
    setSnapshot((await response.json()) as MarketDashboardSnapshot);
  };

  return {
    snapshot,
    connectionState,
    error,
    fetchSnapshot,
    triggerScan
  };
}

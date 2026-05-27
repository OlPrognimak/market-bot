"use client";

import { useEffect, useMemo, useState } from "react";
import { authFetch } from "@/lib/auth";
import { backendWsUrl } from "@/lib/websocket";
import type { MarketDashboardSnapshot } from "../types/market-dashboard";

type ConnectionState = "connecting" | "connected" | "reconnecting" | "disconnected";

export function useMarketDashboardSocket(token: string) {
  const [snapshot, setSnapshot] = useState<MarketDashboardSnapshot | null>(null);
  const [connectionState, setConnectionState] = useState<ConnectionState>("connecting");
  const [error, setError] = useState<string | null>(null);
  const wsUrl = useMemo(() => backendWsUrl(token), [token]);

  useEffect(() => {
    let socket: WebSocket | null = null;
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    let connectTimer: ReturnType<typeof setTimeout> | null = null;
    let manuallyClosed = false;

    const connect = () => {
      connectTimer = null;
      if (manuallyClosed) {
        return;
      }

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

    connectTimer = setTimeout(connect, 0);

    return () => {
      manuallyClosed = true;
      if (connectTimer) {
        clearTimeout(connectTimer);
      }
      if (reconnectTimer) {
        clearTimeout(reconnectTimer);
      }
      if (socket?.readyState === WebSocket.OPEN) {
        socket.close();
      }
    };
  }, [wsUrl]);

  const fetchSnapshot = async () => {
    const response = await authFetch(token, "/api/dashboard/snapshot");
    setSnapshot((await response.json()) as MarketDashboardSnapshot);
  };

  const triggerScan = async () => {
    const response = await authFetch(token, "/api/dashboard/scan", {
      method: "POST"
    });
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

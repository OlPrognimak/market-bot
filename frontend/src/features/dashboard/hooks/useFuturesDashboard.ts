"use client";
import { useCallback, useState } from "react";
import { authFetch } from "@/lib/auth";
import type { FuturesSnapshot } from "../types/market-dashboard";

export function useFuturesDashboard(token: string) {
  const [snapshot, setSnapshot] = useState<FuturesSnapshot | null>(null);
  const fetchSnapshot = useCallback(async () => {
    const response = await authFetch(token, "/api/futures-dashboard/snapshot");
    const next = await response.json() as FuturesSnapshot;
    setSnapshot(next); return next;
  }, [token]);
  const triggerScan = useCallback(async () => {
    const response = await authFetch(token, "/api/futures-dashboard/scan", { method: "POST" });
    const next = await response.json() as FuturesSnapshot;
    setSnapshot(next); return next;
  }, [token]);
  return { snapshot, fetchSnapshot, triggerScan };
}

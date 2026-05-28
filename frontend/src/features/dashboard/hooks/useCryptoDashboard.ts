"use client";

import { useCallback, useState } from "react";
import { authFetch } from "@/lib/auth";
import type { CryptoDashboardSnapshot } from "../types/market-dashboard";

export function useCryptoDashboard(token: string) {
  const [snapshot, setSnapshot] = useState<CryptoDashboardSnapshot | null>(null);

  const fetchSnapshot = useCallback(async () => {
    const response = await authFetch(token, "/api/crypto-dashboard/snapshot");
    const nextSnapshot = await response.json() as CryptoDashboardSnapshot;
    setSnapshot(nextSnapshot);
    return nextSnapshot;
  }, [token]);

  const triggerScan = useCallback(async () => {
    const response = await authFetch(token, "/api/crypto-dashboard/scan", { method: "POST" });
    const nextSnapshot = await response.json() as CryptoDashboardSnapshot;
    setSnapshot(nextSnapshot);
    return nextSnapshot;
  }, [token]);

  return { snapshot, fetchSnapshot, triggerScan };
}

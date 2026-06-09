"use client";

import { useCallback, useState } from "react";
import { authFetch } from "@/lib/auth";
import type { ExtendedHoursSnapshot, MarketSession } from "../types/market-dashboard";

export function useExtendedHoursDashboard(token: string) {
  const [snapshot, setSnapshot] = useState<ExtendedHoursSnapshot | null>(null);

  const fetchSnapshot = useCallback(async (session?: MarketSession) => {
    const query = session ? `?session=${session}` : "";
    const response = await authFetch(token, `/api/extended-hours-dashboard/snapshot${query}`);
    const next = await response.json() as ExtendedHoursSnapshot;
    setSnapshot(next);
    return next;
  }, [token]);

  const triggerScan = useCallback(async (session?: MarketSession) => {
    const query = session ? `?session=${session}` : "";
    const response = await authFetch(token, `/api/extended-hours-dashboard/scan${query}`, { method: "POST" });
    const next = await response.json() as ExtendedHoursSnapshot;
    setSnapshot(next);
    return next;
  }, [token]);

  return { snapshot, fetchSnapshot, triggerScan };
}

import { backendHttpUrl } from "@/lib/websocket";
import { authFetch } from "@/lib/auth";
import type { PortfolioAnalysis, PortfolioImport, PortfolioMarkerResponse, PortfolioProviderType } from "./types";

export async function uploadPortfolioCsv(
  token: string,
  file: File,
  providerType: PortfolioProviderType = "REVOLUT"
): Promise<PortfolioImport> {
  const form = new FormData();
  form.append("file", file);
  const response = await fetch(
    `${backendHttpUrl()}/api/portfolio/imports?providerType=${encodeURIComponent(providerType)}`,
    {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
      body: form
    }
  );
  if (!response.ok) {
    throw new Error((await response.text()) || `Upload failed with ${response.status}`);
  }
  return response.json() as Promise<PortfolioImport>;
}

export async function fetchPortfolioImports(token: string): Promise<PortfolioImport[]> {
  const response = await authFetch(token, "/api/portfolio/imports");
  return response.json() as Promise<PortfolioImport[]>;
}

export async function fetchPortfolioAnalysis(
  token: string,
  filters: { from?: string; to?: string; ticker?: string } = {}
): Promise<PortfolioAnalysis> {
  const params = new URLSearchParams();
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);
  if (filters.ticker) params.set("ticker", filters.ticker);
  const query = params.toString();
  const response = await authFetch(token, `/api/portfolio/analysis${query ? `?${query}` : ""}`);
  return response.json() as Promise<PortfolioAnalysis>;
}

export async function fetchPortfolioMarkers(token: string, ticker: string): Promise<PortfolioMarkerResponse> {
  const response = await authFetch(token, `/api/portfolio/markers/${encodeURIComponent(ticker)}`);
  return response.json() as Promise<PortfolioMarkerResponse>;
}

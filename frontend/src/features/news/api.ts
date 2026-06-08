import { authFetch } from "@/lib/auth";
import type { InstrumentType, NewsInsight, NewsRefreshResponse, NewsResearchLayer, NewsResearchResponse } from "./types";

export async function refreshNewsInsights(
  token: string,
  instrumentType: InstrumentType,
  symbol: string
): Promise<NewsRefreshResponse> {
  const response = await authFetch(token, "/api/news/insights/refresh", {
    method: "POST",
    body: JSON.stringify({ instrumentType, symbol })
  });
  return response.json() as Promise<NewsRefreshResponse>;
}

export async function fetchNewsInsights(
  token: string,
  instrumentType: InstrumentType,
  symbol: string
): Promise<NewsInsight[]> {
  const response = await authFetch(
    token,
    `/api/news/insights?instrumentType=${instrumentType}&symbol=${encodeURIComponent(symbol)}&limit=20`
  );
  return response.json() as Promise<NewsInsight[]>;
}

export async function fetchNewsRefreshStatus(
  token: string,
  instrumentType: InstrumentType,
  symbol: string
): Promise<NewsRefreshResponse> {
  const response = await authFetch(
    token,
    `/api/news/insights/refresh-status?instrumentType=${instrumentType}&symbol=${encodeURIComponent(symbol)}`
  );
  return response.json() as Promise<NewsRefreshResponse>;
}

export async function startNewsResearch(
  token: string,
  instrumentType: InstrumentType,
  symbol: string,
  layer: NewsResearchLayer
): Promise<NewsResearchResponse> {
  const response = await authFetch(token, "/api/news/insights/research", {
    method: "POST",
    body: JSON.stringify({ instrumentType, symbol, layer })
  });
  return response.json() as Promise<NewsResearchResponse>;
}

export async function fetchNewsResearchStatus(
  token: string,
  instrumentType: InstrumentType,
  symbol: string,
  layer: NewsResearchLayer
): Promise<NewsResearchResponse> {
  const response = await authFetch(
    token,
    `/api/news/insights/research-status?instrumentType=${instrumentType}&symbol=${encodeURIComponent(symbol)}&layer=${layer}`
  );
  return response.json() as Promise<NewsResearchResponse>;
}

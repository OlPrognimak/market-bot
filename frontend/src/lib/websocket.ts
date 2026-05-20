const DEFAULT_BACKEND_URL = "http://localhost:8080";

export function backendHttpUrl(): string {
  return process.env.NEXT_PUBLIC_MARKET_BOT_API_URL ?? DEFAULT_BACKEND_URL;
}

export function backendWsUrl(): string {
  const httpUrl = backendHttpUrl();
  const url = new URL(httpUrl);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  url.pathname = "/ws/market-dashboard";
  url.search = "";
  return url.toString();
}

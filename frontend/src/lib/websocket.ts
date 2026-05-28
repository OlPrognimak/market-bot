export function backendHttpUrl(): string {
  return process.env.NEXT_PUBLIC_MARKET_BOT_API_URL ?? "";
}

export function backendWsUrl(token?: string): string {
  const explicitHttpUrl = process.env.NEXT_PUBLIC_MARKET_BOT_API_URL;
  const url = explicitHttpUrl
    ? new URL(explicitHttpUrl)
    : new URL(typeof window === "undefined" ? "http://localhost:3000" : window.location.origin);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  url.pathname = "/ws/market-dashboard";
  url.search = "";
  if (token) {
    url.searchParams.set("access_token", token);
  }
  return url.toString();
}

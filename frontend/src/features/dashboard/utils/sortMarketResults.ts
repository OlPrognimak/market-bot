import type { MarketScanResult, SortKey } from "../types/market-dashboard";

export function sortMarketResults(results: MarketScanResult[], sortKey: SortKey): MarketScanResult[] {
  if (sortKey === "backend") {
    return results;
  }

  return [...results].sort((left, right) => {
    if (sortKey === "rolling") {
      return Math.abs(right.rollingDelta) - Math.abs(left.rollingDelta);
    }

    if (sortKey === "delta") {
      return Math.abs(right.delta) - Math.abs(left.delta);
    }

    if (sortKey === "symbol") {
      return left.symbol.localeCompare(right.symbol);
    }

    return new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime();
  });
}

import type { CryptoScanResult, CryptoSortKey } from "../types/market-dashboard";

export function sortCryptoResults(results: CryptoScanResult[], sortKey: CryptoSortKey): CryptoScanResult[] {
  if (sortKey === "backend") {
    return results;
  }

  return [...results].sort((left, right) => {
    if (sortKey === "movement") {
      return Math.abs(right.priceChangePercent) - Math.abs(left.priceChangePercent);
    }

    if (sortKey === "symbol") {
      return left.baseAsset.localeCompare(right.baseAsset);
    }

    if (sortKey === "volume") {
      return right.quoteVolume - left.quoteVolume;
    }

    return new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime();
  });
}

export type MarketDirection = "UP" | "DOWN" | "NEUTRAL";

export type ScanTriggerMode = "BACKEND_SCHEDULED" | "FRONTEND_TRIGGERED" | "BOTH";

export type MarketScanResult = {
  symbol: string;
  companyName: string;
  region?: string | null;
  sector?: string | null;
  exchange?: string | null;
  currency?: string | null;
  priority?: string | null;
  currentPercent: number;
  previousPercent: number;
  delta: number;
  rollingDelta: number;
  rollingWindowSize: number;
  currentPrice: number;
  low: number;
  high: number;
  open: number;
  previousClose: number;
  direction: MarketDirection;
  alert: boolean;
  updatedAt: string;
  messageText?: string | null;
};

export type MarketDashboardSnapshot = {
  lastScanAt: string;
  triggerMode: ScanTriggerMode;
  results: MarketScanResult[];
  topPositiveRolling?: MarketScanResult | null;
  topNegativeRolling?: MarketScanResult | null;
  topPositiveDelta?: MarketScanResult | null;
  topNegativeDelta?: MarketScanResult | null;
};

export type SortKey = "backend" | "rolling" | "delta" | "symbol" | "updatedAt";

export type CryptoScanResult = {
  symbol: string;
  baseAsset: string;
  coinName: string;
  window: string;
  openPrice: number;
  closePrice: number;
  priceChangePercent: number;
  quoteVolume: number;
  direction: MarketDirection;
  alert: boolean;
  updatedAt: string;
  messageText?: string | null;
};

export type CryptoDashboardSnapshot = {
  lastScanAt: string;
  triggerMode: ScanTriggerMode;
  results: CryptoScanResult[];
  topPositive?: CryptoScanResult | null;
  topNegative?: CryptoScanResult | null;
};

export type CryptoSortKey = "backend" | "movement" | "symbol" | "updatedAt" | "volume";

export type MarketChartRange = "today" | "yesterday" | "week" | "month" | "year";

export type MarketChartPoint = {
  time: string;
  price: number;
  percentChange: number;
  delta: number;
  open: number;
  high: number;
  low: number;
  previousClose: number;
};

export type MarketChartResponse = {
  symbol: string;
  range: MarketChartRange;
  requestedFrom: string;
  actualFrom?: string | null;
  actualTo?: string | null;
  fallback: boolean;
  points: MarketChartPoint[];
};

export type MarketSession = "PRE_MARKET" | "REGULAR" | "POST_MARKET" | "CLOSED" | "UNKNOWN";
export type FreshnessStatus = "LIVE" | "DELAYED" | "STALE" | "UNKNOWN";

export type ExtendedHoursResult = {
  symbol: string;
  companyName: string;
  region?: string | null;
  sector?: string | null;
  exchange?: string | null;
  session: MarketSession;
  price: number;
  sessionMove: number;
  delta: number;
  rolling: number;
  volume: number;
  freshness: FreshnessStatus;
  providerTimestamp: string;
};

export type ExtendedHoursSnapshot = {
  lastScanAt: string;
  dataDate?: string | null;
  fallback: boolean;
  results: ExtendedHoursResult[];
};

export type FuturesResult = {
  symbol: string;
  name: string;
  underlying: string;
  region?: string | null;
  exchange?: string | null;
  currency?: string | null;
  price: number;
  changeFromSettlement: number;
  changeFromOpen: number;
  delta: number;
  rolling: number;
  volume: number;
  freshness: FreshnessStatus;
  providerTimestamp: string;
};

export type FuturesSnapshot = {
  lastScanAt: string;
  results: FuturesResult[];
  topPositive?: FuturesResult | null;
  topNegative?: FuturesResult | null;
};

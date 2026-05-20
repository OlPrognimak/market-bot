export type MarketDirection = "UP" | "DOWN" | "NEUTRAL";

export type ScanTriggerMode = "BACKEND_SCHEDULED" | "FRONTEND_TRIGGERED" | "BOTH";

export type MarketScanResult = {
  symbol: string;
  companyName: string;
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

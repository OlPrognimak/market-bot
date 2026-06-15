export type PortfolioProviderType = "REVOLUT";

export type PortfolioImport = {
  id: number;
  providerType: PortfolioProviderType;
  schemaType: "REVOLUT_ALL_TRANSACTIONS" | "REVOLUT_GAIN_LOSS_STATEMENT";
  originalFileName: string;
  fileHash: string;
  totalRows: number;
  importedRows: number;
  skippedRows: number;
  importedAt: string;
  duplicateFile: boolean;
};

export type PortfolioPosition = {
  ticker: string;
  currency: string;
  quantity: number;
  remainingCostBasis: number;
  averageCost: number;
  currentPrice: number | null;
  marketValue: number | null;
  unrealizedPnl: number | null;
  unrealizedPnlPercent: number | null;
  reconciliationStatus: string;
};

export type PortfolioAnalysis = {
  providerType: PortfolioProviderType;
  transactionCount: number;
  realizedLotCount: number;
  incomeCount: number;
  positions: PortfolioPosition[];
  periodFrom?: string | null;
  periodTo?: string | null;
  selectedTicker?: string | null;
  availableTickers: string[];
  realizedProfitByCurrency: Record<string, number>;
  realizedLossByCurrency: Record<string, number>;
  realizedPnlByCurrency: Record<string, number>;
  incomeByCurrency: Record<string, number>;
};

export type PortfolioMarkerResponse = {
  ticker: string;
  providerType: PortfolioProviderType;
  markers: Array<{
    id: number;
    eventTime: string;
    type: "BUY" | "SELL";
    price: number;
    quantity: number;
    totalAmount: number;
    currency: string;
  }>;
};

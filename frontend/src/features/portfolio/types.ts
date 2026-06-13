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

export type PortfolioTransaction = {
  eventTime: string;
  ticker: string | null;
  transactionType: string;
  quantity: number | null;
  pricePerShare: number | null;
  totalAmount: number;
  currency: string;
};

export type PortfolioAnalysis = {
  providerType: PortfolioProviderType;
  transactionCount: number;
  realizedLotCount: number;
  incomeCount: number;
  positions: PortfolioPosition[];
  realizedPnlByCurrency: Record<string, number>;
  incomeByCurrency: Record<string, number>;
  recentTransactions: PortfolioTransaction[];
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

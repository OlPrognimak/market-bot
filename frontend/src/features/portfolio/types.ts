export type PortfolioProviderType = "REVOLUT" | "TRADE_REPUBLIC";
export type PortfolioProviderFilter = PortfolioProviderType | "ALL";

export type PortfolioImport = {
  id: number;
  providerType: PortfolioProviderType;
  schemaType: "REVOLUT_ALL_TRANSACTIONS" | "REVOLUT_GAIN_LOSS_STATEMENT" | "TRADE_REPUBLIC_TRANSACTION_EXPORT" | "TRADE_REPUBLIC_TAX_OVERVIEW";
  originalFileName: string;
  fileHash: string;
  totalRows: number;
  importedRows: number;
  skippedRows: number;
  importedAt: string;
  duplicateFile: boolean;
};

export type PortfolioPosition = {
  providerType: PortfolioProviderType;
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
  providerType: PortfolioProviderType | null;
  transactionCount: number;
  realizedLotCount: number;
  incomeCount: number;
  positions: PortfolioPosition[];
  periodFrom?: string | null;
  periodTo?: string | null;
  selectedTicker?: string | null;
  selectedProviderType?: PortfolioProviderType | null;
  availableTickers: string[];
  filteredTickerResults: PortfolioFilteredTickerResult[];
  selectedTickerRealizedLots: PortfolioRealizedLotDetail[];
  realizedProfitByCurrency: Record<string, number>;
  realizedLossByCurrency: Record<string, number>;
  realizedPnlByCurrency: Record<string, number>;
  incomeByCurrency: Record<string, number>;
};

export type PortfolioRealizedLotDetail = {
  providerType: PortfolioProviderType;
  acquiredDate: string;
  soldDate: string;
  ticker: string;
  currency: string;
  quantity: number;
  costBasis: number;
  grossProceeds: number;
  realizedProfit: number;
  realizedLoss: number;
  realizedPnl: number;
};

export type PortfolioFilteredTickerResult = {
  providerType: PortfolioProviderType;
  ticker: string;
  currency: string;
  transactionCount: number;
  realizedLotCount: number;
  incomeCount: number;
  realizedProfit: number;
  realizedLoss: number;
  realizedPnl: number;
  income: number;
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

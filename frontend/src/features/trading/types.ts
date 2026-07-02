export type TradingMode = "PAPER" | "LIVE";
export type BrokerType = "PAPER" | "IBKR";
export type OrderSide = "BUY" | "SELL";
export type OrderType = "MARKET" | "LIMIT";
export type TradeOrderStatus = "PREVIEWED" | "REJECTED" | "SUBMITTED" | "FILLED" | "PARTIALLY_FILLED" | "CANCELLED" | "FAILED";

export type TradingStatus = {
  enabled: boolean;
  mode: TradingMode;
  brokerType: BrokerType;
  liveEnabled: boolean;
  ibkrEnabled: boolean;
  ibkrConnected: boolean;
  requireManualConfirmation: boolean;
};

export type IbkrConnectionStatus = {
  enabled: boolean;
  host: string;
  port: number;
  clientId: number;
  accountId?: string | null;
  paperAccountOnly: boolean;
  connected: boolean;
  state: string;
  message: string;
  connectedAt?: string | null;
  disconnectedAt?: string | null;
};

export type TradeOrderRequest = {
  previewId?: string | null;
  symbol: string;
  side: OrderSide;
  quantity?: number | null;
  amount?: number | null;
  orderType: OrderType;
  limitPrice?: number | null;
  currency?: string | null;
  exchange?: string | null;
  stopLossPercent?: number | null;
  takeProfitPercent?: number | null;
  confirmationToken?: string | null;
};

export type TradeOrderPreview = {
  previewId: string;
  symbol: string;
  side: OrderSide;
  orderType: OrderType;
  currentPrice: number;
  estimatedQuantity: number;
  estimatedValue: number;
  currency: string;
  tradingMode: TradingMode;
  allowed: boolean;
  warnings: string[];
  rejectionReasons: string[];
  confirmationToken: string;
  expiresAt: string;
};

export type TradeOrderResult = {
  orderId: number;
  brokerOrderId?: string | null;
  symbol: string;
  side: OrderSide;
  status: TradeOrderStatus;
  submittedAt?: string | null;
  message?: string | null;
};

export type TradingPosition = {
  symbol: string;
  quantity: number;
  avgPrice: number;
  currentPrice: number;
  marketValue: number;
  unrealizedPnl: number;
  unrealizedPnlPercent: number;
  currency: string;
  brokerType: BrokerType;
};

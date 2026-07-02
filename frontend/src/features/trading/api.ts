import { authFetch } from "@/lib/auth";
import type {
  IbkrConnectionStatus,
  TradeOrderPreview,
  TradeOrderRequest,
  TradeOrderResult,
  TradingPosition,
  TradingStatus
} from "./types";

export async function fetchTradingStatus(token: string): Promise<TradingStatus> {
  const response = await authFetch(token, "/api/trading/status");
  return response.json() as Promise<TradingStatus>;
}

export async function fetchTradingPositions(token: string): Promise<TradingPosition[]> {
  const response = await authFetch(token, "/api/trading/positions");
  return response.json() as Promise<TradingPosition[]>;
}

export async function fetchIbkrStatus(token: string): Promise<IbkrConnectionStatus> {
  const response = await authFetch(token, "/api/trading/ibkr/status");
  return response.json() as Promise<IbkrConnectionStatus>;
}

export async function connectIbkr(token: string): Promise<IbkrConnectionStatus> {
  const response = await authFetch(token, "/api/trading/ibkr/connect", { method: "POST" });
  return response.json() as Promise<IbkrConnectionStatus>;
}

export async function disconnectIbkr(token: string): Promise<IbkrConnectionStatus> {
  const response = await authFetch(token, "/api/trading/ibkr/disconnect", { method: "POST" });
  return response.json() as Promise<IbkrConnectionStatus>;
}

export async function previewTradeOrder(token: string, payload: TradeOrderRequest): Promise<TradeOrderPreview> {
  const response = await authFetch(token, "/api/trading/orders/preview", {
    method: "POST",
    body: JSON.stringify(payload)
  });
  return response.json() as Promise<TradeOrderPreview>;
}

export async function submitTradeOrder(token: string, payload: TradeOrderRequest): Promise<TradeOrderResult> {
  const response = await authFetch(token, "/api/trading/orders", {
    method: "POST",
    body: JSON.stringify(payload)
  });
  return response.json() as Promise<TradeOrderResult>;
}

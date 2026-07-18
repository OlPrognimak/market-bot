export type UserRole = "ADMIN" | "USER";
export type UserPropertyType = "BOT" | "WATCHLIST" | "CRYPTO_COIN" | "ALERT_SETTING" | "DASHBOARD_SETTING" | "CUSTOM";
export type UserPropertyValueType = "TEXT" | "SECRET" | "SYMBOL";

export type UserProperty = {
  id?: number;
  userId?: number;
  propertyType: UserPropertyType;
  propertyName: string;
  propertyValue?: string | null;
  enabled?: boolean;
  description?: string | null;
  propertyValueType: UserPropertyValueType;
};

export type AppUser = {
  id: number;
  username: string;
  displayName: string;
  email: string;
  role: UserRole;
  enabled: boolean;
  metadata: Record<string, string>;
  properties: UserProperty[];
  created: string | null;
  modified: string | null;
};

export type AuthSession = {
  token: string;
  user: AppUser;
};

export type UserPayload = {
  username: string;
  password?: string;
  displayName: string;
  email: string;
  role: UserRole;
  enabled: boolean;
  metadata: Record<string, string>;
  properties: UserProperty[];
};

export type SignUpPayload = {
  username: string;
  password: string;
  displayName: string;
  email: string;
  metadata: Record<string, string>;
  properties: UserProperty[];
};

export type WatchlistCatalogItem = {
  symbol: string;
  name: string;
};

export type SymbolValidationResult = {
  symbol: string;
  valid: boolean;
  message?: string | null;
  name?: string | null;
};

export type StockCatalogItem = {
  id: number;
  symbol: string;
  name: string;
  region?: string | null;
  sector?: string | null;
  exchange?: string | null;
  currency?: string | null;
  enabled: boolean;
  priority: string;
};

export type CryptoCatalogItem = {
  id: number;
  symbol: string;
  name: string;
  quoteAsset: string;
  pairSymbol: string;
  enabled: boolean;
};

export type CatalogItemPayload = {
  symbol: string;
  name: string;
  region?: string | null;
  sector?: string | null;
  exchange?: string | null;
  currency?: string | null;
  enabled?: boolean;
  priority?: string | null;
};

export type PortfolioProviderType = "REVOLUT" | "TRADE_REPUBLIC";
export type SystemCredentialType = "AI_PROVIDER" | "MARKET_DATA_PROVIDER" | "MESSAGING_PROVIDER";

export type ProviderSymbolMapping = {
  id: number;
  providerType: PortfolioProviderType;
  sourceSymbol: string;
  sourceSymbolType: string;
  marketProvider: string;
  marketSymbol: string;
  instrumentName?: string | null;
  currency?: string | null;
  enabled: boolean;
  verified: boolean;
  priority: number;
};

export type ProviderSymbolMappingPayload = Omit<ProviderSymbolMapping, "id">;

export type SystemApiCredential = {
  id: number;
  credentialType: SystemCredentialType;
  providerName: string;
  displayName: string;
  maskedSecret: string;
  hasSecret: boolean;
  enabled: boolean;
  active: boolean;
  description?: string | null;
};

export type SystemApiCredentialPayload = {
  credentialType: SystemCredentialType;
  providerName: string;
  displayName: string;
  secretValue?: string | null;
  enabled: boolean;
  active: boolean;
  description?: string | null;
};

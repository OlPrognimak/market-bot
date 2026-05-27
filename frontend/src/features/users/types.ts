export type UserRole = "ADMIN" | "USER";
export type UserPropertyType = "BOT" | "WATCHLIST" | "CRYPTO_COIN" | "CUSTOM";
export type UserPropertyValueType = "TEXT" | "SECRET" | "SYMBOL";

export type UserProperty = {
  id?: number;
  userId?: number;
  propertyType: UserPropertyType;
  propertyName: string;
  propertyValue?: string | null;
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

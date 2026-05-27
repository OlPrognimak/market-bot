"use client";

import { FormEvent, useState } from "react";
import { signUp, storeSession } from "@/lib/auth";
import type { AuthSession, SignUpPayload, UserProperty } from "../types";
import { WatchlistEditor, watchlistProperties, type WatchlistRow } from "./WatchlistEditor";

type Props = {
  onLogin: (session: AuthSession) => void;
  onShowLogin: () => void;
};

export function SignUpPage({ onLogin, onShowLogin }: Props) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [telegramBotToken, setTelegramBotToken] = useState("");
  const [telegramChatId, setTelegramChatId] = useState("");
  const [telegramEnabled, setTelegramEnabled] = useState(true);
  const [whatsAppEnabled, setWhatsAppEnabled] = useState(false);
  const [whatsAppChatId, setWhatsAppChatId] = useState("");
  const [rollingThreshold, setRollingThreshold] = useState("0.8");
  const [deltaThreshold, setDeltaThreshold] = useState("0.0001");
  const [watchlistRows, setWatchlistRows] = useState<WatchlistRow[]>([
    { propertyName: "AAPL", propertyValue: "Apple", enabled: true },
    { propertyName: "NVDA", propertyValue: "NVIDIA", enabled: true }
  ]);
  const [cryptoCoinsText, setCryptoCoinsText] = useState("BTC");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const session = await signUp(toPayload({
        username,
        password,
        displayName,
        email,
        telegramBotToken,
        telegramChatId,
        telegramEnabled,
        whatsAppEnabled,
        whatsAppChatId,
        rollingThreshold,
        deltaThreshold,
        watchlistRows,
        cryptoCoinsText
      }));
      storeSession(session);
      onLogin(session);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Registration failed");
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="auth-page">
      <form className="auth-panel signup-panel" onSubmit={submit}>
        <h1>Create Account</h1>
        <label>
          Username
          <input value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" required />
        </label>
        <label>
          Display name
          <input value={displayName} onChange={(event) => setDisplayName(event.target.value)} autoComplete="name" required />
        </label>
        <label>
          Email
          <input value={email} onChange={(event) => setEmail(event.target.value)} type="email" autoComplete="email" required />
        </label>
        <label>
          Password
          <input
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            type="password"
            minLength={8}
            autoComplete="new-password"
            required
          />
        </label>

        <fieldset>
          <legend>Bot Configuration</legend>
          <label className="checkbox-label">
            <input checked={telegramEnabled} onChange={(event) => setTelegramEnabled(event.target.checked)} type="checkbox" />
            Telegram enabled
          </label>
          <label>
            Telegram bot token
            <input value={telegramBotToken} onChange={(event) => setTelegramBotToken(event.target.value)} />
          </label>
          <label>
            Telegram chat id
            <input value={telegramChatId} onChange={(event) => setTelegramChatId(event.target.value)} />
          </label>
          <label className="checkbox-label">
            <input checked={whatsAppEnabled} onChange={(event) => setWhatsAppEnabled(event.target.checked)} type="checkbox" />
            WhatsApp enabled
          </label>
          <label>
            WhatsApp chat id
            <input value={whatsAppChatId} onChange={(event) => setWhatsAppChatId(event.target.value)} />
          </label>
        </fieldset>

        <fieldset>
          <legend>Alert Settings</legend>
          <label>
            Rolling threshold
            <input value={rollingThreshold} onChange={(event) => setRollingThreshold(event.target.value)} inputMode="decimal" />
          </label>
          <label>
            Delta threshold
            <input value={deltaThreshold} onChange={(event) => setDeltaThreshold(event.target.value)} inputMode="decimal" />
          </label>
        </fieldset>

        <fieldset>
          <legend>Watchlist</legend>
          <WatchlistEditor rows={watchlistRows} setRows={setWatchlistRows} />
        </fieldset>

        <fieldset>
          <legend>Crypto Coins</legend>
          <textarea value={cryptoCoinsText} onChange={(event) => setCryptoCoinsText(event.target.value)} rows={3} />
        </fieldset>

        {error ? <div className="error-banner">{error}</div> : null}
        <button type="submit" disabled={busy}>
          {busy ? "Creating" : "Sign Up"}
        </button>
        <p className="auth-switch">
          Already have an account?{" "}
          <button type="button" className="link-button" onClick={onShowLogin}>
            Sign In
          </button>
        </p>
      </form>
    </main>
  );
}

type FormValues = {
  username: string;
  password: string;
  displayName: string;
  email: string;
  telegramBotToken: string;
  telegramChatId: string;
  telegramEnabled: boolean;
  whatsAppEnabled: boolean;
  whatsAppChatId: string;
  rollingThreshold: string;
  deltaThreshold: string;
  watchlistRows: WatchlistRow[];
  cryptoCoinsText: string;
};

function toPayload(values: FormValues): SignUpPayload {
  return {
    username: values.username.trim(),
    password: values.password,
    displayName: values.displayName.trim(),
    email: values.email.trim(),
    metadata: {},
    properties: [
      ...botProperties(values),
      ...alertProperties(values.rollingThreshold, values.deltaThreshold),
      ...watchlistProperties(values.watchlistRows),
      ...cryptoCoinProperties(values.cryptoCoinsText)
    ]
  };
}

function botProperties(values: FormValues): UserProperty[] {
  const properties: UserProperty[] = [
    {
      propertyType: "BOT",
      propertyName: "telegram-enabled",
      propertyValue: String(values.telegramEnabled),
      enabled: true,
      description: "Telegram messenger enabled",
      propertyValueType: "TEXT"
    },
    {
      propertyType: "BOT",
      propertyName: "telegram-bot-token",
      propertyValue: values.telegramBotToken.trim(),
      enabled: values.telegramEnabled,
      description: "Telegram bot token",
      propertyValueType: "SECRET"
    },
    {
      propertyType: "BOT",
      propertyName: "telegram-chat-id",
      propertyValue: values.telegramChatId.trim(),
      enabled: values.telegramEnabled,
      description: "Telegram chat id",
      propertyValueType: "TEXT"
    },
    {
      propertyType: "BOT",
      propertyName: "whatsapp-enabled",
      propertyValue: String(values.whatsAppEnabled),
      enabled: true,
      description: "WhatsApp messenger enabled",
      propertyValueType: "TEXT"
    },
    {
      propertyType: "BOT",
      propertyName: "whatsapp-chat-id",
      propertyValue: values.whatsAppChatId.trim(),
      enabled: values.whatsAppEnabled,
      description: "WhatsApp chat id",
      propertyValueType: "TEXT"
    }
  ];

  return properties.filter((property) => property.propertyValue);
}

function alertProperties(rollingThreshold: string, deltaThreshold: string): UserProperty[] {
  const properties: UserProperty[] = [
    {
      propertyType: "ALERT_SETTING",
      propertyName: "alert-rolling-threshold",
      propertyValue: rollingThreshold.trim(),
      enabled: true,
      description: "Rolling movement threshold",
      propertyValueType: "TEXT"
    },
    {
      propertyType: "ALERT_SETTING",
      propertyName: "alert-delta-threshold",
      propertyValue: deltaThreshold.trim(),
      enabled: true,
      description: "Delta movement threshold",
      propertyValueType: "TEXT"
    }
  ];

  return properties.filter((property) => property.propertyValue);
}

function cryptoCoinProperties(value: string): UserProperty[] {
  return value.split(/[\n, ]/)
    .map((coin) => coin.trim().toUpperCase())
    .filter(Boolean)
    .map((coin): UserProperty => ({
      propertyType: "CRYPTO_COIN",
      propertyName: coin,
      propertyValue: coin,
      enabled: true,
      description: "Crypto coin",
      propertyValueType: "SYMBOL"
    }));
}

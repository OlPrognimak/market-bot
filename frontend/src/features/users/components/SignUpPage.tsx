"use client";

import { FormEvent, useState } from "react";
import { signUp, storeSession } from "@/lib/auth";
import type { AuthSession, SignUpPayload, UserProperty } from "../types";

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
  const [watchlistText, setWatchlistText] = useState("AAPL: Apple\nNVDA: NVIDIA");
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
        watchlistText,
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
          <input value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" />
        </label>
        <label>
          Display name
          <input value={displayName} onChange={(event) => setDisplayName(event.target.value)} autoComplete="name" />
        </label>
        <label>
          Email
          <input value={email} onChange={(event) => setEmail(event.target.value)} type="email" autoComplete="email" />
        </label>
        <label>
          Password
          <input
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            type="password"
            minLength={8}
            autoComplete="new-password"
          />
        </label>

        <fieldset>
          <legend>Bot Configuration</legend>
          <label>
            Telegram bot token
            <input value={telegramBotToken} onChange={(event) => setTelegramBotToken(event.target.value)} />
          </label>
          <label>
            Telegram chat id
            <input value={telegramChatId} onChange={(event) => setTelegramChatId(event.target.value)} />
          </label>
        </fieldset>

        <fieldset>
          <legend>Watchlist</legend>
          <textarea value={watchlistText} onChange={(event) => setWatchlistText(event.target.value)} rows={4} />
        </fieldset>

        <fieldset>
          <legend>Crypto Coins</legend>
          <textarea value={cryptoCoinsText} onChange={(event) => setCryptoCoinsText(event.target.value)} rows={3} />
        </fieldset>

        {error ? <div className="error-banner">{error}</div> : null}
        <button type="submit" disabled={busy || !username || password.length < 8 || !displayName || !email}>
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
  watchlistText: string;
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
      ...botProperties(values.telegramBotToken, values.telegramChatId),
      ...watchlistProperties(values.watchlistText),
      ...cryptoCoinProperties(values.cryptoCoinsText)
    ]
  };
}

function botProperties(telegramBotToken: string, telegramChatId: string): UserProperty[] {
  const properties: UserProperty[] = [
    {
      propertyType: "BOT",
      propertyName: "telegram-bot-token",
      propertyValue: telegramBotToken.trim(),
      description: "Telegram bot token",
      propertyValueType: "SECRET"
    },
    {
      propertyType: "BOT",
      propertyName: "telegram-chat-id",
      propertyValue: telegramChatId.trim(),
      description: "Telegram chat id",
      propertyValueType: "TEXT"
    }
  ];

  return properties.filter((property) => property.propertyValue);
}

function watchlistProperties(value: string): UserProperty[] {
  return value.split("\n")
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [symbol, ...rest] = line.split(/[:=]/);
      return {
        propertyType: "WATCHLIST",
        propertyName: symbol.trim().toUpperCase(),
        propertyValue: rest.join(":").trim() || symbol.trim().toUpperCase(),
        description: "Watchlist instrument",
        propertyValueType: "SYMBOL"
      } satisfies UserProperty;
    })
    .filter((property) => property.propertyName);
}

function cryptoCoinProperties(value: string): UserProperty[] {
  return value.split(/[\n, ]/)
    .map((coin) => coin.trim().toUpperCase())
    .filter(Boolean)
    .map((coin): UserProperty => ({
      propertyType: "CRYPTO_COIN",
      propertyName: coin,
      propertyValue: coin,
      description: "Crypto coin",
      propertyValueType: "SYMBOL"
    }));
}

"use client";

import { FormEvent, useState } from "react";
import { updateCurrentUser } from "@/lib/auth";
import type { AppUser, UserPayload, UserProperty } from "../types";
import { WatchlistEditor, watchlistProperties, watchlistToRows, type WatchlistRow } from "./WatchlistEditor";

type Props = {
  token: string;
  currentUser: AppUser;
  onSave: (user: AppUser) => void;
  onBack: () => void;
};

export function UserSettingsPage({ token, currentUser, onSave, onBack }: Props) {
  const [displayName, setDisplayName] = useState(currentUser.displayName);
  const [email, setEmail] = useState(currentUser.email);
  const [password, setPassword] = useState("");
  const [watchlistRows, setWatchlistRows] = useState<WatchlistRow[]>(watchlistToRows(currentUser.properties));
  const [cryptoRows, setCryptoRows] = useState<WatchlistRow[]>(watchlistToRows(currentUser.properties, "CRYPTO_COIN"));
  const [sharesRollingThreshold, setSharesRollingThreshold] = useState(alertValue(currentUser.properties, "shares-alert-rolling-threshold", "alert-rolling-threshold", "0.8"));
  const [sharesDeltaThreshold, setSharesDeltaThreshold] = useState(alertValue(currentUser.properties, "shares-alert-delta-threshold", "alert-delta-threshold", "0.0001"));
  const [cryptoRollingThreshold, setCryptoRollingThreshold] = useState(alertValue(currentUser.properties, "crypto-alert-rolling-threshold", "alert-rolling-threshold", "0.8"));
  const [cryptoDeltaThreshold, setCryptoDeltaThreshold] = useState(alertValue(currentUser.properties, "crypto-alert-delta-threshold", "alert-delta-threshold", "0.0001"));
  const [telegramEnabled, setTelegramEnabled] = useState(property(currentUser.properties, "BOT", "telegram-enabled")?.propertyValue !== "false");
  const [telegramBotToken, setTelegramBotToken] = useState(property(currentUser.properties, "BOT", "telegram-bot-token")?.propertyValue ?? "");
  const [telegramChatId, setTelegramChatId] = useState(property(currentUser.properties, "BOT", "telegram-chat-id")?.propertyValue ?? "");
  const [whatsAppEnabled, setWhatsAppEnabled] = useState(property(currentUser.properties, "BOT", "whatsapp-enabled")?.propertyValue === "true");
  const [whatsAppChatId, setWhatsAppChatId] = useState(property(currentUser.properties, "BOT", "whatsapp-chat-id")?.propertyValue ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [watchlistInvalid, setWatchlistInvalid] = useState(false);
  const [cryptoInvalid, setCryptoInvalid] = useState(false);
  const settingsInvalid = watchlistInvalid || cryptoInvalid;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const user = await updateCurrentUser(token, {
        username: currentUser.username,
        password: password || undefined,
        displayName: displayName.trim(),
        email: email.trim(),
        role: currentUser.role,
        enabled: currentUser.enabled,
        metadata: currentUser.metadata,
        properties: [
          ...watchlistProperties(watchlistRows),
          ...watchlistProperties(cryptoRows, { propertyType: "CRYPTO_COIN", description: "Crypto coin" }),
          ...alertProperties(currentUser.properties, {
            sharesRollingThreshold,
            sharesDeltaThreshold,
            cryptoRollingThreshold,
            cryptoDeltaThreshold
          }),
          ...botProperties(currentUser.properties, { telegramEnabled, telegramBotToken, telegramChatId, whatsAppEnabled, whatsAppChatId })
        ]
      });
      onSave(user);
      onBack();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save settings");
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="dashboard">
      <header className="dashboard-header">
        <div>
          <h1>User Settings</h1>
          <p>Update your watchlist, alert thresholds, and message delivery.</p>
        </div>
      </header>
      <form className="user-form" onSubmit={submit}>
        <label>
          Display name
          <input value={displayName} onChange={(event) => setDisplayName(event.target.value)} />
        </label>
        <label>
          Email
          <input value={email} onChange={(event) => setEmail(event.target.value)} type="email" />
        </label>
        <label>
          New password
          <input value={password} onChange={(event) => setPassword(event.target.value)} type="password" minLength={8} />
        </label>
        <fieldset>
          <legend>Stock Watchlist</legend>
          <WatchlistEditor
            rows={watchlistRows}
            setRows={setWatchlistRows}
            token={token}
            catalogType="stocks"
            pickerLabel="Symbol"
            onValidationStateChange={setWatchlistInvalid}
          />
        </fieldset>
        <fieldset>
          <legend>Crypto Coins</legend>
          <WatchlistEditor
            rows={cryptoRows}
            setRows={setCryptoRows}
            addLabel="Add Manually"
            token={token}
            catalogType="crypto"
            pickerLabel="Coin"
            onValidationStateChange={setCryptoInvalid}
          />
        </fieldset>
        <fieldset>
          <legend>Shares Alert Settings</legend>
          <label>
            Rolling threshold
            <input value={sharesRollingThreshold} onChange={(event) => setSharesRollingThreshold(event.target.value)} inputMode="decimal" />
          </label>
          <label>
            Delta threshold
            <input value={sharesDeltaThreshold} onChange={(event) => setSharesDeltaThreshold(event.target.value)} inputMode="decimal" />
          </label>
        </fieldset>
        <fieldset>
          <legend>Crypto Alert Settings</legend>
          <label>
            Rolling threshold
            <input value={cryptoRollingThreshold} onChange={(event) => setCryptoRollingThreshold(event.target.value)} inputMode="decimal" />
          </label>
          <label>
            Delta threshold
            <input value={cryptoDeltaThreshold} onChange={(event) => setCryptoDeltaThreshold(event.target.value)} inputMode="decimal" />
          </label>
        </fieldset>
        <fieldset>
          <legend>Messengers</legend>
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
        {error ? <div className="error-banner">{error}</div> : null}
        <div className="form-actions">
          <button type="submit" disabled={busy || settingsInvalid || !displayName || !email}>{busy ? "Saving" : "Save Settings"}</button>
        </div>
      </form>
    </main>
  );
}

function property(properties: AppUser["properties"], type: string, name: string): UserProperty | undefined {
  return properties.find((item) => item.propertyType === type && item.propertyName === name);
}

function alertValue(properties: AppUser["properties"], name: string, fallbackName: string, defaultValue: string): string {
  return property(properties, "ALERT_SETTING", name)?.propertyValue
    ?? property(properties, "ALERT_SETTING", fallbackName)?.propertyValue
    ?? defaultValue;
}

function alertProperties(properties: AppUser["properties"], values: {
  sharesRollingThreshold: string;
  sharesDeltaThreshold: string;
  cryptoRollingThreshold: string;
  cryptoDeltaThreshold: string;
}): UserProperty[] {
  return [
    { id: property(properties, "ALERT_SETTING", "shares-alert-rolling-threshold")?.id, propertyType: "ALERT_SETTING", propertyName: "shares-alert-rolling-threshold", propertyValue: values.sharesRollingThreshold.trim(), enabled: true, description: "Shares rolling movement threshold", propertyValueType: "TEXT" },
    { id: property(properties, "ALERT_SETTING", "shares-alert-delta-threshold")?.id, propertyType: "ALERT_SETTING", propertyName: "shares-alert-delta-threshold", propertyValue: values.sharesDeltaThreshold.trim(), enabled: true, description: "Shares delta movement threshold", propertyValueType: "TEXT" },
    { id: property(properties, "ALERT_SETTING", "crypto-alert-rolling-threshold")?.id, propertyType: "ALERT_SETTING", propertyName: "crypto-alert-rolling-threshold", propertyValue: values.cryptoRollingThreshold.trim(), enabled: true, description: "Crypto rolling movement threshold", propertyValueType: "TEXT" },
    { id: property(properties, "ALERT_SETTING", "crypto-alert-delta-threshold")?.id, propertyType: "ALERT_SETTING", propertyName: "crypto-alert-delta-threshold", propertyValue: values.cryptoDeltaThreshold.trim(), enabled: true, description: "Crypto delta movement threshold", propertyValueType: "TEXT" }
  ];
}

function botProperties(properties: AppUser["properties"], values: {
  telegramEnabled: boolean;
  telegramBotToken: string;
  telegramChatId: string;
  whatsAppEnabled: boolean;
  whatsAppChatId: string;
}): UserProperty[] {
  const botProperties: UserProperty[] = [
    { id: property(properties, "BOT", "telegram-enabled")?.id, propertyType: "BOT", propertyName: "telegram-enabled", propertyValue: String(values.telegramEnabled), enabled: true, description: "Telegram messenger enabled", propertyValueType: "TEXT" },
    { id: property(properties, "BOT", "telegram-bot-token")?.id, propertyType: "BOT", propertyName: "telegram-bot-token", propertyValue: values.telegramBotToken.trim(), enabled: values.telegramEnabled, description: "Telegram bot token", propertyValueType: "SECRET" },
    { id: property(properties, "BOT", "telegram-chat-id")?.id, propertyType: "BOT", propertyName: "telegram-chat-id", propertyValue: values.telegramChatId.trim(), enabled: values.telegramEnabled, description: "Telegram chat id", propertyValueType: "TEXT" },
    { id: property(properties, "BOT", "whatsapp-enabled")?.id, propertyType: "BOT", propertyName: "whatsapp-enabled", propertyValue: String(values.whatsAppEnabled), enabled: true, description: "WhatsApp messenger enabled", propertyValueType: "TEXT" },
    { id: property(properties, "BOT", "whatsapp-chat-id")?.id, propertyType: "BOT", propertyName: "whatsapp-chat-id", propertyValue: values.whatsAppChatId.trim(), enabled: values.whatsAppEnabled, description: "WhatsApp chat id", propertyValueType: "TEXT" }
  ];

  return botProperties.filter((item) => item.propertyValue);
}

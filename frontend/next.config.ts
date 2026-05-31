import type { NextConfig } from "next";

const backendUrl = process.env.MARKET_BOT_BACKEND_URL ?? "http://localhost:8080";
const allowedDevOrigins = (process.env.MARKET_BOT_ALLOWED_DEV_ORIGINS ?? "")
  .split(",")
  .map((origin) => origin.trim())
  .filter(Boolean);

const nextConfig: NextConfig = {
  allowedDevOrigins,
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${backendUrl}/api/:path*`
      },
      {
        source: "/ws/:path*",
        destination: `${backendUrl}/ws/:path*`
      },
    ];
  }
};

export default nextConfig;

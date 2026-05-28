import type { NextConfig } from "next";

const backendUrl = process.env.MARKET_BOT_BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${backendUrl}/api/:path*`
      },
      {
        source: "/ws/:path*",
        destination: `${backendUrl}/ws/:path*`
      }
    ];
  }
};

export default nextConfig;

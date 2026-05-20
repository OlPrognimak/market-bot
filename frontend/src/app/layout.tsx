import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Market Bot Dashboard",
  description: "Live market scan dashboard"
};

export default function RootLayout({
  children
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}

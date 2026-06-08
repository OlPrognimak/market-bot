export type InstrumentType = "SHARE" | "CRYPTO";
export type NewsDirection = "UP" | "DOWN" | "NEUTRAL" | "UNKNOWN";

export type NewsInsight = {
  id: number;
  instrumentType: InstrumentType;
  symbol: string;
  instrumentName: string;
  direction: NewsDirection;
  confidence: number;
  impactScore: number;
  timeHorizon: "INTRADAY" | "DAYS" | "WEEKS" | "UNKNOWN";
  summary: string;
  reason: string;
  alertRecommended: boolean;
  title: string;
  articleSummary: string | null;
  sourceName: string | null;
  sourceUrl: string;
  publishedAt: string;
  analyzedAt: string;
};

export type NewsRefreshResponse = {
  collectedArticles: number;
  newInsights: number;
  refreshing: boolean;
  insights: NewsInsight[];
};

export type NewsResearchLayer =
  | "CURRENT_SITUATION"
  | "FUNDAMENTAL_ANALYSIS"
  | "SCENARIO_ANALYSIS"
  | "COMPLETE_RESEARCH";

export type NewsResearchResponse = {
  instrumentType: InstrumentType;
  symbol: string;
  layer: NewsResearchLayer;
  running: boolean;
  content: string | null;
  sources: { title: string; url: string }[];
  error: string | null;
  generatedAt: string | null;
};

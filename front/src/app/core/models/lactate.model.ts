export interface LactateStep {
  stepOrder?: number;
  speedMs: number | null;
  speedKmh?: number | null;
  hr: number | null;
  lactate: number | null;
  rpe?: number | null;
  durationS?: number | null;
}

export interface LtDetection {
  baseline: number | null;
  lt1Threshold: number | null;
  lt1Ms: number | null;
  lt2Ms: number | null;
  lt1Kmh: number | null;
  lt2Kmh: number | null;
  fcLt1: number | null;
  fcLt2: number | null;
}

export interface LactateTest {
  id: string;
  testType: string;
  testDate: string;
  notes: string | null;
  lactateRest: number | null;
  hrRest: number | null;
  hrMax: number | null;
  lt1Ms: number | null;
  lt2Ms: number | null;
  fcLt1: number | null;
  fcLt2: number | null;
  steps: LactateStep[];
}

export interface LoadDistribution {
  domain1Pct: number;
  domain2Pct: number;
  domain3Pct: number;
}

export interface Load {
  acuteLoad7d: number;
  chronicLoad28d: number;
  ratio: number | null;
  monotony: number | null;
  distribution7d: LoadDistribution;
  distribution28d: LoadDistribution;
  sessions7d: number;
  sessions28d: number;
}

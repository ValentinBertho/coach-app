export type UnavailabilityReason = 'INJURY' | 'ILLNESS' | 'VACATION' | 'PERSONAL' | 'OTHER';

export interface Unavailability {
  id: string;
  startDate: string;
  endDate: string;
  reason: UnavailabilityReason;
  notes: string | null;
}

export interface UnavailabilityRequest {
  startDate: string;
  endDate: string;
  reason: UnavailabilityReason;
  notes?: string | null;
}

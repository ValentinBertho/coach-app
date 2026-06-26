export interface CalendarNote {
  id: string;
  athleteId: string;
  noteDate: string;
  text: string;
}

export interface CalendarNoteRequest {
  noteDate: string;
  text: string;
}

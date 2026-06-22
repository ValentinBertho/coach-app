import { UserRole } from './user.model';

export interface Message {
  id: string;
  body: string;
  senderRole: UserRole;
  senderName: string;
  senderUserId: string;
  workoutId: string | null;
  createdAt: string;
}

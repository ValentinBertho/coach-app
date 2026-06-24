import { UserRole } from './user.model';

export interface Message {
  id: string;
  body: string;
  senderRole: UserRole;
  senderName: string;
  senderUserId: string;
  workoutId: string | null;
  attachmentId: string | null;
  attachmentFilename: string | null;
  attachmentContentType: string | null;
  createdAt: string;
}

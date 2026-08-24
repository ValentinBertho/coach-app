/** Qui voit un groupe : tous les coachs du club, ou son créateur et ses invités. */
export type GroupVisibility = 'CLUB' | 'PRIVATE';

export interface TrainingGroup {
  id: string;
  name: string;
  athleteCount: number;
  visibility: GroupVisibility;
  ownerCoachId: string | null;
  invitedCoachIds: string[];
  /** Le coach connecté peut-il renommer ce groupe, l'ouvrir ou le refermer ? */
  canManage: boolean;
}

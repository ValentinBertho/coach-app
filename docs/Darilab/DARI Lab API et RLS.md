# DARI Lab Training — Contrats API & sécurité RLS

> Compagnon technique du cahier des charges. À lire avec `dari-types.ts`.
> Sert à cadrer Codex sur les formats d'échange et les policies Supabase.

---

## 1. Exemples de payloads API

Format général : toutes les réponses sont enveloppées `{ data, error }`. Erreurs HTTP standard (400/401/403/404/409/422/500). Auth par JWT Supabase dans l'en-tête `Authorization: Bearer <token>`.

### POST /api/pp/calc/e1rm
Calcule le 1RM estimé (sans persister).
```jsonc
// Request
{ "charge_kg": 15, "reps": 6, "rir": null, "formula": "nuzzo" }
// Response
{
  "data": {
    "e1rm_kg": 16.764,
    "pct_1rm_at_test": 89.48,
    "work_zones": {
      "puissance_vitesse": { "min_kg": 5.0, "max_kg": 8.4 },
      "puissance_force":   { "min_kg": 8.4, "max_kg": 11.7 },
      "force_max":         { "min_kg": 11.7 },
      "hypertrophie":      { "min_kg": 5.0, "max_kg": 13.4 }
    }
  },
  "error": null
}
```

### POST /api/pp/sessions/:id/assign
Assigne une séance de force au calendrier d'un athlète. Le serveur calcule les charges cibles depuis le 1RM de l'athlète et fige un `session_snapshot`.
```jsonc
// Request
{
  "athlete_id": "uuid",
  "date": "2025-06-19",
  "required_fields": { "charge": true, "reps": true, "rpe": true, "rir": false, "pain": false, "comment": true }
}
// Response 201
{ "data": { "scheduled_strength_session_id": "uuid", "computed_loads_preview": "Squat 96-102kg (RIR2)" }, "error": null }
```

### POST /api/pp/results
Enregistre un retour athlète série par série. Déclenche le recalcul e1RM + la mise à jour de `athlete_1rm_profile` si dépassement, et le calcul de charge méca/métabolique.
```jsonc
// Request
{
  "scheduled_session_id": "uuid",
  "results": [
    { "session_exercise_id": "uuid", "exercise_id": "uuid", "set_number": 1, "charge_kg": 100, "reps_done": 5, "rir_done": 2, "pain": 0 },
    { "session_exercise_id": "uuid", "exercise_id": "uuid", "set_number": 2, "charge_kg": 100, "reps_done": 5, "rir_done": 1, "pain": 0 }
  ],
  "session": { "session_rpe": 7, "session_fatigue": 4, "session_pain": 1, "session_comment": "RAS" }
}
// Response
{
  "data": {
    "updated_1rm": [{ "exercise_id": "uuid", "old_kg": 122, "new_kg": 125, "source": "estimated" }],
    "alerts": [],
    "mechanical_load_au": 540, "metabolic_load_au": 280
  },
  "error": null
}
```

### POST /api/tests/lactate/detect
Détecte LT1/LT2 à partir des paliers (sans persister).
```jsonc
// Request
{
  "rest_lactate_mmol": 1.0,
  "steps": [
    { "speed_kmh": 10, "lactate_mmol": 1.2 },
    { "speed_kmh": 12, "lactate_mmol": 1.6 },
    { "speed_kmh": 14, "lactate_mmol": 2.4 },
    { "speed_kmh": 16, "lactate_mmol": 4.1 },
    { "speed_kmh": 18, "lactate_mmol": 7.8 }
  ]
}
// Response
{
  "data": {
    "lt1": { "speed_kmh": 12.4, "lactate_mmol": 1.5 },
    "lt2": { "speed_kmh": 15.7, "lactate_mmol": 3.6 },
    "baseline": 1.0, "lt1Threshold": 1.5
  },
  "error": null
}
```

### POST /api/athletes/:id/groups
Assigne/retire un athlète de groupes (remplace l'ensemble).
```jsonc
// Request
{ "group_ids": ["uuid-elite-trail", "uuid-marathon-automne"] }
// Response
{ "data": { "athlete_id": "uuid", "groups": ["uuid-elite-trail", "uuid-marathon-automne"] }, "error": null }
```

### POST /api/athletes/:id/ownership
Bascule privé ↔ club. Le serveur refuse club→privé si une permission active existe.
```jsonc
// Request
{ "ownership_type": "private" }
// Response 409 si bloqué
{ "data": null, "error": { "code": "ACTIVE_PERMISSION_EXISTS", "message": "Léa Roussel a un accès écriture jusqu'au 28/06. Retirez-le d'abord." } }
```

---

## 2. RLS Policies Supabase (SQL)

> Activer RLS sur **toutes** les tables. Le client serveur agit avec le JWT de l'utilisateur (`auth.uid()`).
> Pattern clé : un coach accède à un athlète s'il est référent OU s'il a une permission non expirée OU si l'athlète est `club` et que le coach est owner/principal du même club.

### Fonction helper : un coach peut-il lire cet athlète ?
```sql
CREATE OR REPLACE FUNCTION coach_can_read_athlete(p_athlete_id UUID)
RETURNS BOOLEAN LANGUAGE sql SECURITY DEFINER STABLE AS $$
  SELECT EXISTS (
    -- référent direct
    SELECT 1 FROM coach_athlete_relations car
    WHERE car.athlete_id = p_athlete_id
      AND car.coach_id = auth.uid() AND car.active
  ) OR EXISTS (
    -- permission explicite non expirée
    SELECT 1 FROM athlete_coach_permissions acp
    WHERE acp.athlete_id = p_athlete_id
      AND acp.coach_id = auth.uid()
      AND (acp.expires_at IS NULL OR acp.expires_at > NOW())
  ) OR EXISTS (
    -- athlète "club" + coach owner/principal du même club
    SELECT 1
    FROM coach_athlete_relations car
    JOIN club_members cm ON cm.club_id = car.club_id
    WHERE car.athlete_id = p_athlete_id
      AND car.ownership_type = 'club'
      AND cm.coach_id = auth.uid()
      AND cm.active
      AND cm.club_role IN ('owner', 'coach_principal')
  );
$$;
```

### Fonction helper : un coach peut-il écrire sur cet athlète ?
```sql
CREATE OR REPLACE FUNCTION coach_can_write_athlete(p_athlete_id UUID)
RETURNS BOOLEAN LANGUAGE sql SECURITY DEFINER STABLE AS $$
  SELECT EXISTS (
    SELECT 1 FROM coach_athlete_relations car
    WHERE car.athlete_id = p_athlete_id
      AND car.coach_id = auth.uid() AND car.active AND car.is_referent
  ) OR EXISTS (
    SELECT 1 FROM athlete_coach_permissions acp
    WHERE acp.athlete_id = p_athlete_id
      AND acp.coach_id = auth.uid()
      AND acp.permission = 'write'
      AND (acp.expires_at IS NULL OR acp.expires_at > NOW())
  );
$$;
```

### Policies — athlete_profiles
```sql
ALTER TABLE athlete_profiles ENABLE ROW LEVEL SECURITY;

CREATE POLICY athlete_reads_own ON athlete_profiles
  FOR SELECT USING (athlete_id = auth.uid());

CREATE POLICY coach_reads_athlete ON athlete_profiles
  FOR SELECT USING (coach_can_read_athlete(athlete_id));

CREATE POLICY coach_writes_athlete ON athlete_profiles
  FOR UPDATE USING (coach_can_write_athlete(athlete_id));
```

### Policies — scheduled_strength_sessions
```sql
ALTER TABLE scheduled_strength_sessions ENABLE ROW LEVEL SECURITY;

-- L'athlète lit ses séances et peut UNIQUEMENT déplacer (scheduled_date)
CREATE POLICY athlete_reads_own_sessions ON scheduled_strength_sessions
  FOR SELECT USING (athlete_id = auth.uid());

CREATE POLICY athlete_moves_session ON scheduled_strength_sessions
  FOR UPDATE USING (athlete_id = auth.uid())
  WITH CHECK (athlete_id = auth.uid());
-- ⚠️ Restreindre les colonnes modifiables (scheduled_date, moved_by_athlete,
-- + champs de retour) via un trigger BEFORE UPDATE qui rejette toute
-- modification de session_snapshot / required_fields par un athlète.

CREATE POLICY coach_full_sessions ON scheduled_strength_sessions
  FOR ALL USING (coach_can_read_athlete(athlete_id))
  WITH CHECK (coach_can_write_athlete(athlete_id));
```

### Trigger anti-modification (athlète déplace mais ne modifie pas)
```sql
CREATE OR REPLACE FUNCTION enforce_athlete_move_only()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  IF auth.uid() = NEW.athlete_id THEN
    -- Un athlète ne peut changer que la date, le flag, et son retour
    IF NEW.session_snapshot IS DISTINCT FROM OLD.session_snapshot
       OR NEW.required_fields IS DISTINCT FROM OLD.required_fields
       OR NEW.coach_id IS DISTINCT FROM OLD.coach_id THEN
      RAISE EXCEPTION 'Un athlète ne peut pas modifier le contenu d''une séance';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER trg_enforce_move_only
  BEFORE UPDATE ON scheduled_strength_sessions
  FOR EACH ROW EXECUTE FUNCTION enforce_athlete_move_only();
```

### Policies — bibliothèques (exercices, séances) : visibilité coach/club
```sql
ALTER TABLE pp_exercises ENABLE ROW LEVEL SECURITY;

CREATE POLICY exercises_owner_or_club ON pp_exercises
  FOR ALL USING (
    coach_id = auth.uid()
    OR (club_id IS NOT NULL AND EXISTS (
      SELECT 1 FROM club_members cm
      WHERE cm.club_id = pp_exercises.club_id
        AND cm.coach_id = auth.uid() AND cm.active
    ))
  );
```

**À répliquer** sur `strength_sessions`, `strength_cycles`, `sessions_library`, `cycles_library`, `athlete_groups` (même logique coach_id OR club partagé).

---

## 3. Points de vigilance sécurité
- Ne jamais exposer `sync_tokens` côté client (service role uniquement).
- Pas d'hébergement vidéo natif en V1 : les vidéos d'athlètes transitent par WhatsApp (hors application). Seuls des liens externes de démonstration d'exercice (YouTube/Vimeo) sont stockés, en clair, dans `pp_exercises.video_url`.
- Vérifier les permissions **dans l'API** en plus des RLS (défense en profondeur) avant les calculs coûteux.
- Une permission expirée (`expires_at < now()`) ne doit jamais donner accès : toujours filtrer dans les requêtes ET les policies.

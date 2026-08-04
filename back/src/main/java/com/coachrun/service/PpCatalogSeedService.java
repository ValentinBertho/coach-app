package com.coachrun.service;

import com.coachrun.entity.Club;
import com.coachrun.entity.PpExercise;
import com.coachrun.entity.SessionCategory;
import com.coachrun.entity.enums.CategoryDomain;
import com.coachrun.entity.enums.EquipmentType;
import com.coachrun.entity.enums.ExerciseCategory;
import com.coachrun.entity.enums.ExerciseLevel;
import com.coachrun.entity.enums.MuscleGroup;
import com.coachrun.repository.PpExerciseRepository;
import com.coachrun.repository.SessionCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Provisionne le répertoire de préparation physique d'un club : catégories de séance et
 * bibliothèque d'exercices. Seed applicatif — idempotent, il n'agit que si le club n'a rien.
 *
 * <p><b>Pourquoi.</b> Seules les zones d'entraînement étaient provisionnées ; la bibliothèque
 * d'exercices, elle, démarrait vide. Un préparateur physique devait donc saisir à la main chacun
 * des quarante à soixante exercices de son répertoire — nom, catégorie, groupe musculaire,
 * matériel, consignes — <em>avant</em> de pouvoir prescrire sa première séance de force. C'est
 * plusieurs heures de saisie avant la première valeur perçue, sur un produit qu'on essaie : le
 * point d'abandon le plus probable d'un coach non accompagné. Le jeu de démonstration qui remplit
 * tout cela est réservé au profil de développement, donc absent en production.</p>
 *
 * <p><b>Ce que contient le catalogue.</b> Un répertoire de coureur, pas une salle de musculation :
 * chaîne postérieure, unilatéral, pied-cheville, gainage et pliométrie dominent, parce que ce sont
 * les familles qui tiennent un coureur debout. Chaque exercice porte ses consignes et ses
 * contre-indications — un catalogue sans consigne oblige à tout rouvrir pour prescrire.</p>
 *
 * <p>Le contenu est un point de départ, pas une norme : tout est éditable, archivable et
 * complétable par le coach, exactement comme les zones seedées.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PpCatalogSeedService {

    private final PpExerciseRepository exerciseRepository;
    private final SessionCategoryRepository categoryRepository;

    /** Définition d'un exercice du catalogue. */
    private record Seed(String name, ExerciseCategory category, ExerciseLevel level,
                        Set<MuscleGroup> muscles, Set<EquipmentType> equipment,
                        String objective, String instructions, String contraindications) {
    }

    /** Catégories de séance de préparation physique proposées d'emblée. */
    private static final List<String> STRENGTH_CATEGORIES = List.of(
            "Force maximale", "Puissance & pliométrie", "Gainage & tronc",
            "Prévention & réathlétisation", "Mobilité", "Circuit général");

    private static final List<Seed> CATALOG = List.of(
            // --- Chaîne postérieure : le socle du coureur ---
            new Seed("Soulevé de terre roumain", ExerciseCategory.FORCE_MAX, ExerciseLevel.INTERMEDIAIRE,
                    Set.of(MuscleGroup.ISCHIOS, MuscleGroup.FESSIERS, MuscleGroup.DOS),
                    Set.of(EquipmentType.BARRE, EquipmentType.HALTERES),
                    "Renforcer la chaîne postérieure en excentrique",
                    "Jambes quasi tendues, dos neutre, barre au contact des cuisses. Descendre jusqu'à "
                            + "la tension des ischios, sans arrondir le bas du dos.",
                    "Lombalgie aiguë, hernie discale non consolidée."),
            new Seed("Nordic hamstring", ExerciseCategory.FORCE_MAX, ExerciseLevel.AVANCE,
                    Set.of(MuscleGroup.ISCHIOS), Set.of(EquipmentType.POIDS_DU_CORPS),
                    "Force excentrique des ischios — prévention de la lésion myo-aponévrotique",
                    "À genoux, chevilles bloquées, descendre le plus lentement possible en gardant "
                            + "le bassin dans l'axe du tronc. Amortir avec les mains.",
                    "Douleur ischio en cours, tendinopathie proximale en phase aiguë."),
            new Seed("Hip thrust", ExerciseCategory.FORCE_MAX, ExerciseLevel.INTERMEDIAIRE,
                    Set.of(MuscleGroup.FESSIERS, MuscleGroup.HANCHE),
                    Set.of(EquipmentType.BARRE), "Extension de hanche sous charge",
                    "Dos appuyé sur un banc, menton rentré. Monter jusqu'à l'alignement "
                            + "épaules-hanches-genoux, marquer un temps en haut.", null),
            new Seed("Pont fessier unilatéral", ExerciseCategory.ENDURANCE_MUSCULAIRE, ExerciseLevel.DEBUTANT,
                    Set.of(MuscleGroup.FESSIERS, MuscleGroup.ISCHIOS), Set.of(EquipmentType.POIDS_DU_CORPS),
                    "Réveil fessier et dissociation gauche/droite",
                    "Un pied au sol, l'autre jambe tendue. Monter sans laisser le bassin s'affaisser "
                            + "du côté libre.", null),
            new Seed("Good morning", ExerciseCategory.FORCE_MAX, ExerciseLevel.AVANCE,
                    Set.of(MuscleGroup.ISCHIOS, MuscleGroup.DOS), Set.of(EquipmentType.BARRE),
                    "Charnière de hanche sous charge axiale",
                    "Barre sur le haut du dos, flexion de hanche dos neutre, charge légère au début.",
                    "Antécédent lombaire récent."),

            // --- Quadriceps et unilatéral ---
            new Seed("Squat arrière", ExerciseCategory.FORCE_MAX, ExerciseLevel.INTERMEDIAIRE,
                    Set.of(MuscleGroup.QUADRICEPS, MuscleGroup.FESSIERS), Set.of(EquipmentType.BARRE),
                    "Force maximale des membres inférieurs",
                    "Pieds largeur d'épaules, descendre au moins jusqu'à la cuisse parallèle en "
                            + "gardant les talons au sol.", null),
            new Seed("Fente avant", ExerciseCategory.FORCE_MAX, ExerciseLevel.DEBUTANT,
                    Set.of(MuscleGroup.QUADRICEPS, MuscleGroup.FESSIERS),
                    Set.of(EquipmentType.HALTERES, EquipmentType.POIDS_DU_CORPS),
                    "Force unilatérale et contrôle du genou",
                    "Genou avant dans l'axe du deuxième orteil, tronc vertical, poussée par le talon.",
                    "Syndrome fémoro-patellaire douloureux en flexion."),
            new Seed("Fente bulgare", ExerciseCategory.FORCE_MAX, ExerciseLevel.INTERMEDIAIRE,
                    Set.of(MuscleGroup.QUADRICEPS, MuscleGroup.FESSIERS),
                    Set.of(EquipmentType.HALTERES, EquipmentType.BOX),
                    "Force unilatérale sous grande amplitude",
                    "Pied arrière surélevé, descente contrôlée, bassin stable.", null),
            new Seed("Step-up sur box", ExerciseCategory.FORCE_MAX, ExerciseLevel.DEBUTANT,
                    Set.of(MuscleGroup.QUADRICEPS, MuscleGroup.FESSIERS),
                    Set.of(EquipmentType.BOX, EquipmentType.HALTERES),
                    "Poussée unilatérale spécifique à la foulée",
                    "Monter sans pousser sur la jambe restée au sol, redescendre en freinant.", null),
            new Seed("Split squat isométrique", ExerciseCategory.ISOMETRIE, ExerciseLevel.DEBUTANT,
                    Set.of(MuscleGroup.QUADRICEPS), Set.of(EquipmentType.POIDS_DU_CORPS),
                    "Endurance de force du quadriceps",
                    "Position de fente basse tenue, genou avant à 90°, respiration continue.", null),

            // --- Pied, cheville, mollet : là où se jouent la plupart des blessures ---
            new Seed("Extension mollets debout", ExerciseCategory.FORCE_MAX, ExerciseLevel.DEBUTANT,
                    Set.of(MuscleGroup.MOLLETS), Set.of(EquipmentType.POIDS_DU_CORPS, EquipmentType.HALTERES),
                    "Force du triceps sural, genou tendu (gastrocnémiens)",
                    "Amplitude complète, temps d'arrêt en haut, descente lente.", null),
            new Seed("Extension mollets genou fléchi", ExerciseCategory.FORCE_MAX, ExerciseLevel.DEBUTANT,
                    Set.of(MuscleGroup.MOLLETS), Set.of(EquipmentType.MACHINE, EquipmentType.HALTERES),
                    "Force du soléaire — le muscle le plus sollicité en course",
                    "Genou à 30° de flexion, mouvement uniquement à la cheville.", null),
            new Seed("Excentrique mollet sur marche", ExerciseCategory.REATHLETISATION, ExerciseLevel.DEBUTANT,
                    Set.of(MuscleGroup.MOLLETS, MuscleGroup.PIED_CHEVILLE), Set.of(EquipmentType.POIDS_DU_CORPS),
                    "Protocole excentrique — tendinopathie d'Achille",
                    "Monter à deux pieds, descendre à un pied en trois secondes, sous le niveau de la marche.",
                    "Rupture tendineuse, douleur > 5/10 pendant l'exercice."),
            new Seed("Équilibre unipodal", ExerciseCategory.PREVENTION, ExerciseLevel.DEBUTANT,
                    Set.of(MuscleGroup.PIED_CHEVILLE), Set.of(EquipmentType.POIDS_DU_CORPS),
                    "Proprioception de cheville",
                    "Tenir sur un pied, regard fixe puis yeux fermés. Progresser sur surface instable.", null),
            new Seed("Toe yoga", ExerciseCategory.PREVENTION, ExerciseLevel.DEBUTANT,
                    Set.of(MuscleGroup.PIED_CHEVILLE), Set.of(EquipmentType.POIDS_DU_CORPS),
                    "Contrôle des muscles intrinsèques du pied",
                    "Décoller le gros orteil seul, puis les quatre autres seuls, pied à plat.", null),

            // --- Tronc et gainage ---
            new Seed("Gainage ventral", ExerciseCategory.GAINAGE, ExerciseLevel.DEBUTANT,
                    Set.of(MuscleGroup.TRONC), Set.of(EquipmentType.POIDS_DU_CORPS),
                    "Anti-extension lombaire",
                    "Bassin rétroversé, fessiers serrés, ne pas creuser le bas du dos.", null),
            new Seed("Gainage latéral", ExerciseCategory.GAINAGE, ExerciseLevel.DEBUTANT,
                    Set.of(MuscleGroup.TRONC, MuscleGroup.HANCHE), Set.of(EquipmentType.POIDS_DU_CORPS),
                    "Anti-inclinaison, stabilité du bassin en appui unipodal",
                    "Corps aligné, hanche haute, épaule à l'aplomb du coude.", null),
            new Seed("Dead bug", ExerciseCategory.GAINAGE, ExerciseLevel.DEBUTANT,
                    Set.of(MuscleGroup.TRONC), Set.of(EquipmentType.POIDS_DU_CORPS),
                    "Dissociation ceintures, contrôle lombaire",
                    "Bas du dos plaqué au sol pendant tout le mouvement. Bras et jambe opposés.", null),
            new Seed("Pallof press", ExerciseCategory.GAINAGE, ExerciseLevel.INTERMEDIAIRE,
                    Set.of(MuscleGroup.TRONC), Set.of(EquipmentType.ELASTIQUE, EquipmentType.POULIE),
                    "Anti-rotation du tronc",
                    "Élastique perpendiculaire au corps, tendre les bras sans laisser le buste tourner.", null),
            new Seed("Bird dog", ExerciseCategory.GAINAGE, ExerciseLevel.DEBUTANT,
                    Set.of(MuscleGroup.TRONC, MuscleGroup.DOS), Set.of(EquipmentType.POIDS_DU_CORPS),
                    "Stabilité lombo-pelvienne en quadrupédie",
                    "Bras et jambe opposés tendus, bassin immobile — poser un bâton sur le bas du dos "
                            + "pour contrôler.", null),

            // --- Hanche et prévention ---
            new Seed("Abduction hanche élastique", ExerciseCategory.PREVENTION, ExerciseLevel.DEBUTANT,
                    Set.of(MuscleGroup.FESSIERS, MuscleGroup.HANCHE), Set.of(EquipmentType.ELASTIQUE),
                    "Moyen fessier — contrôle du valgus de genou",
                    "Élastique au-dessus des genoux, ouvrir sans basculer le bassin.", null),
            new Seed("Monster walk", ExerciseCategory.PREVENTION, ExerciseLevel.DEBUTANT,
                    Set.of(MuscleGroup.FESSIERS, MuscleGroup.HANCHE), Set.of(EquipmentType.ELASTIQUE),
                    "Activation fessière en charge",
                    "Demi-squat, pas latéraux sans rapprocher complètement les pieds.", null),
            new Seed("Copenhagen adducteurs", ExerciseCategory.PREVENTION, ExerciseLevel.AVANCE,
                    Set.of(MuscleGroup.HANCHE), Set.of(EquipmentType.BOX),
                    "Prévention de la pubalgie",
                    "Gainage latéral, jambe supérieure en appui sur un banc. Commencer genou fléchi.",
                    "Pubalgie en phase douloureuse."),

            // --- Pliométrie et puissance ---
            new Seed("Sauts sur box", ExerciseCategory.PLIOMETRIE, ExerciseLevel.INTERMEDIAIRE,
                    Set.of(MuscleGroup.QUADRICEPS, MuscleGroup.MOLLETS), Set.of(EquipmentType.BOX),
                    "Puissance concentrique des membres inférieurs",
                    "Réception souple au centre de la box, redescendre en marchant.",
                    "Tendinopathie rotulienne ou achilléenne en phase douloureuse."),
            new Seed("Pogo jumps", ExerciseCategory.PLIOMETRIE, ExerciseLevel.DEBUTANT,
                    Set.of(MuscleGroup.MOLLETS, MuscleGroup.PIED_CHEVILLE), Set.of(EquipmentType.POIDS_DU_CORPS),
                    "Raideur du complexe pied-cheville (stiffness)",
                    "Petits rebonds genoux quasi tendus, temps de contact le plus court possible.", null),
            new Seed("Drop jump", ExerciseCategory.PLIOMETRIE, ExerciseLevel.AVANCE,
                    Set.of(MuscleGroup.QUADRICEPS, MuscleGroup.MOLLETS), Set.of(EquipmentType.BOX),
                    "Cycle étirement-détente",
                    "Descendre de la box, rebondir immédiatement. Volume faible, qualité maximale.",
                    "Charge de course élevée dans les 48 h, douleur tendineuse."),
            new Seed("Bondissements horizontaux", ExerciseCategory.PLIOMETRIE, ExerciseLevel.INTERMEDIAIRE,
                    Set.of(MuscleGroup.FESSIERS, MuscleGroup.MOLLETS), Set.of(EquipmentType.POIDS_DU_CORPS),
                    "Puissance horizontale, spécifique à la propulsion",
                    "Foulées bondissantes sur 20 à 30 m, chercher l'amplitude sans perdre la fréquence.", null),
            new Seed("Épaulé debout", ExerciseCategory.PUISSANCE, ExerciseLevel.AVANCE,
                    Set.of(MuscleGroup.QUADRICEPS, MuscleGroup.DOS, MuscleGroup.EPAULE), Set.of(EquipmentType.BARRE),
                    "Puissance globale, triple extension",
                    "Mouvement technique : apprendre à vide avant de charger.",
                    "Épaule ou poignet limités en mobilité."),
            new Seed("Swing kettlebell", ExerciseCategory.PUISSANCE, ExerciseLevel.INTERMEDIAIRE,
                    Set.of(MuscleGroup.FESSIERS, MuscleGroup.ISCHIOS), Set.of(EquipmentType.KETTLEBELL),
                    "Explosivité de la charnière de hanche",
                    "Le mouvement vient des hanches, pas des bras. Dos neutre, fessiers serrés en haut.",
                    "Lombalgie aiguë."),

            // --- Haut du corps : ce qui sert la course, pas plus ---
            new Seed("Tractions", ExerciseCategory.FORCE_MAX, ExerciseLevel.AVANCE,
                    Set.of(MuscleGroup.DOS, MuscleGroup.HAUT_DU_CORPS), Set.of(EquipmentType.BARRE),
                    "Force du haut du corps",
                    "Amplitude complète, épaules basses en début de traction.", null),
            new Seed("Pompes", ExerciseCategory.ENDURANCE_MUSCULAIRE, ExerciseLevel.DEBUTANT,
                    Set.of(MuscleGroup.HAUT_DU_CORPS, MuscleGroup.TRONC), Set.of(EquipmentType.POIDS_DU_CORPS),
                    "Endurance du haut du corps et gainage",
                    "Corps aligné de la tête aux talons, coudes à 45°.", null),
            new Seed("Rowing haltère", ExerciseCategory.HYPERTROPHIE, ExerciseLevel.INTERMEDIAIRE,
                    Set.of(MuscleGroup.DOS), Set.of(EquipmentType.HALTERES),
                    "Équilibre postural — contrepoids au travail antérieur",
                    "Dos neutre, tirer le coude vers l'arrière sans tourner le buste.", null),

            // --- Mobilité ---
            new Seed("Mobilité de cheville au mur", ExerciseCategory.MOBILITE, ExerciseLevel.DEBUTANT,
                    Set.of(MuscleGroup.PIED_CHEVILLE), Set.of(EquipmentType.POIDS_DU_CORPS),
                    "Amplitude de flexion dorsale — condition d'une foulée efficace",
                    "Genou vers le mur sans décoller le talon, mesurer la distance pied-mur.", null),
            new Seed("Ouverture de hanche 90/90", ExerciseCategory.MOBILITE, ExerciseLevel.DEBUTANT,
                    Set.of(MuscleGroup.HANCHE), Set.of(EquipmentType.POIDS_DU_CORPS),
                    "Rotation interne et externe de hanche",
                    "Assis, deux genoux à 90°, basculer d'un côté à l'autre sans forcer.", null),
            new Seed("Étirement psoas en fente", ExerciseCategory.MOBILITE, ExerciseLevel.DEBUTANT,
                    Set.of(MuscleGroup.HANCHE), Set.of(EquipmentType.POIDS_DU_CORPS),
                    "Extension de hanche — souvent limitée chez le coureur assis la journée",
                    "Bassin en rétroversion, pousser la hanche vers l'avant sans creuser le dos.", null),

            // --- Réathlétisation ---
            new Seed("Isométrique quadriceps au mur", ExerciseCategory.REATHLETISATION, ExerciseLevel.DEBUTANT,
                    Set.of(MuscleGroup.QUADRICEPS), Set.of(EquipmentType.POIDS_DU_CORPS),
                    "Charge sans mouvement — première étape après une douleur rotulienne",
                    "Dos au mur, genoux à 60°, tenir sans douleur au-delà de 3/10.",
                    "Douleur > 3/10 pendant ou après l'exercice."),
            new Seed("Leg curl", ExerciseCategory.REATHLETISATION, ExerciseLevel.DEBUTANT,
                    Set.of(MuscleGroup.ISCHIOS), Set.of(EquipmentType.LEG_CURL),
                    "Renforcement analytique des ischios",
                    "Amplitude complète, phase excentrique freinée.", null),
            new Seed("Marche en charge (rucking)", ExerciseCategory.ENDURANCE_MUSCULAIRE, ExerciseLevel.DEBUTANT,
                    Set.of(MuscleGroup.QUADRICEPS, MuscleGroup.MOLLETS, MuscleGroup.TRONC),
                    Set.of(EquipmentType.AUTRE),
                    "Volume d'appui sans impact — utile en reprise ou en préparation trail",
                    "Sac chargé de 10 à 20 % du poids de corps, terrain vallonné.", null));

    /**
     * Provisionne le club s'il n'a encore aucun exercice. Idempotent : un club qui a commencé à
     * construire sa bibliothèque n'est jamais touché, même s'il a tout archivé.
     */
    @Transactional
    public void seedDefaultsIfEmpty(Club club) {
        if (exerciseRepository.existsByClubId(club.getId())) {
            return;
        }
        seedCategories(club);
        for (Seed seed : CATALOG) {
            PpExercise e = new PpExercise();
            e.setClub(club);
            e.setName(seed.name());
            e.setCategory(seed.category());
            e.setLevel(seed.level());
            e.setMuscleGroups(new java.util.HashSet<>(seed.muscles()));
            e.setEquipment(new java.util.HashSet<>(seed.equipment()));
            e.setObjective(seed.objective());
            e.setInstructions(seed.instructions());
            e.setContraindications(seed.contraindications());
            exerciseRepository.save(e);
        }
        log.info("[seed] Catalogue de préparation physique provisionné pour le club {} ({} exercices).",
                club.getId(), CATALOG.size());
    }

    private void seedCategories(Club club) {
        if (!categoryRepository.findByClubIdAndDomainOrderBySortOrderAscNameAsc(
                club.getId(), CategoryDomain.STRENGTH).isEmpty()) {
            return;
        }
        int order = 0;
        for (String name : STRENGTH_CATEGORIES) {
            SessionCategory c = new SessionCategory();
            c.setClub(club);
            c.setDomain(CategoryDomain.STRENGTH);
            c.setName(name);
            c.setSortOrder(order++);
            categoryRepository.save(c);
        }
    }
}

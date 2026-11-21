# FinLife V2 — moteur mensuel et sessions interactives

Package : `com.tuna.finlife.simulation`. Aucun accès à Compose, aux interfaces, au réseau,
à une horloge ou à un état global mutable. Tous les résultats financiers proviennent des
transitions appliquées. La session est une valeur explicite ; chaque opération retourne
une nouvelle session et laisse l'ancienne utilisable.

## V1 et réutilisation

`ScenarioCatalog`, `ScenarioDefinition` et `ScenarioId` sont réutilisés : seuls **S005,
S037, S040** sont actifs. Les définitions officielles restent inchangées.
`FinancialSimulation` expose maintenant `startSession` et `effectiveMonthlyRate`.
Les anciennes fonctions annuelles (`simulate`, `plans`, `SimulationComparisons`, etc.)
restent disponibles **uniquement pour compatibilité V1**, avec leurs anciennes hypothèses,
leurs Double et leurs résultats historiques. Elles ne sont pas appelées par la V2.
Ne pas utiliser ces fonctions annuelles pour représenter un parcours V2.
Les sorties annuelles V2 viennent exclusivement de `MonthlyComparisons.annualSummaries`.

La V2 réutilise les conventions de réconciliation, l'ordre des opérations et les métadonnées
V1, avec un nouveau stockage en centimes et une progression mensuelle. La règle de versement
investi a changé pour éviter le financement des placements par un découvert.

## Origine des données

Les métadonnées de `scenario-library-120.csv` ont été retranscrites depuis les définitions
explicitement fournies par l'équipe ; aucun téléchargement ou import CSV n'est effectué.
**Tous les montants, taux et probabilités sont des hypothèses FinLife, pas des chiffres
empiriques UBS ni des prévisions.**

| ID | Catégorie officielle | Scénario | Référence | Intervention |
|---|---|---|---|---|
| S005 | Education & skills | Internship year | Continue studies | Paid internship year |
| S037 | Housing & property | Rent increase | Stable rent | Annual rent rises |
| S040 | Housing & property | Unexpected maintenance | Normal upkeep | Major repair bill |

`MonthlyProfile.profileId` prépare les profils UBS. Le constructeur n'invente aucun champ
manquant ; `MonthlyProfile.alexia()` est une fabrique explicite du profil fictif.
`DataProvenance` distingue `HISTORICAL_SYNTHETIC_DATA`, `USER_INPUT`, `TEAM_ASSUMPTION`,
`CHALLENGE_SCENARIO_DEFINITION` et `SIMULATED_RESULT`. Le profil et la configuration portent
leur provenance ; chaque événement sépare définition et hypothèses numériques, avec
`numericProvenance` configurable. `SourcedValue<T>` permet des valeurs annotées individuellement.
`HistoricalMonthlyObservation` conserve les champs absents comme null et reste séparé du
moteur : ses transactions ne sont jamais mélangées automatiquement aux flux simulés.

## Profil et temps

Alexia : 20 ans, Lausanne, CHF, cash 8 000, investissements et dette nuls. Type de logement
`UNKNOWN` (aucune supposition sur une vie seule ou en colocation). Revenus étudiants
nominaux 1 200 CHF/mois ; loyer 600, assurance maladie 250, courses 250, autres dépenses
300 CHF/mois. Ces quatre dépenses sont indexées sur une inflation annuelle de 2 % par défaut.
L'objectif nominal de 350 000 CHF est dû au mois 120, indépendamment de l'horizon simulé.
`session.goal` indique la date observée et si l'échéance a effectivement été simulée.

Horizon : 1 à 1 200 mois (100 ans), notamment 6, 12, 24, 120. N transitions, N+1 états.
L'état m ouvre le mois m ; ses événements affectent l'état m+1. Les identifiants de mois
sont des indices relatifs à la date initiale, sans calendrier civil ni fiscalité.
Tous les mois ont un résultat, même sans événement. Les événements planifiés doivent
appartenir à l'horizon ; leurs conséquences persistantes peuvent le dépasser.
`unsimulatedConsequences()` indique les études, stages et autres effets futurs non calculés.

## Précision et règles financières

`Money` stocke des `Long` en centimes : `Money(3500)` ou `Money.chf(35)`. Les sommes et
réconciliations sont exactes au centime. Limite explicite : ±9 000 000 000 000 centimes
par montant et résultat intermédiaire. Les dépassements sont rejetés, jamais tronqués.
Les taux/facteurs restent des Double. Chaque ligne calculée est arrondie au centime, au
plus proche, demi-centime à l'extérieur de zéro ; une tolérance de 1e−9 centime corrige
le bruit binaire près des égalités décimales. Pas d'arrondi du total d'un cumul déjà arrondi.

Facteur d'inflation à m : `(1 + annualInflation)^(m / 12.0)`.
Taux mensuel effectif : `(1 + annualRate)^(1 / 12.0) - 1`.
Patrimoine réel de l'état m : patrimoine nominal / facteur d'inflation à m, arrondi au centime.
Inflation, croissance salariale et rendement annuels : finis, strictement supérieurs à −100 %
et au plus 100 % ; intérêt de dette de 0 à 100 %. Les valeurs extrêmes peuvent dépasser
la borne monétaire avant la fin d'un long horizon et sont alors rejetées explicitement.

- Dépenses = dépenses récurrentes + études + exceptionnel + coûts de décisions + intérêts.
- Flux net = revenus + emprunts explicites − dépenses − capital remboursé.
- Cash suivant = cash + flux net − versement investi.
- Investissements suivants = investissements + rendement arrondi + versement investi.
- Dette suivante = dette + emprunts explicites − capital remboursé.
- Patrimoine = cash + investissements − dette.
- Variation de patrimoine = revenus − dépenses + rendement.

Versement en fin de mois : minimum entre `part × max(revenus − dépenses − capital remboursé, 0)`
et `max(cash initial + revenus − dépenses − capital remboursé, 0)`, arrondi au centime.
La part vaut 0 par défaut. Les emprunts sont exclus du financement des versements.
Une trésorerie négative doit d'abord être résorbée. Aucun prêt, vente d'actifs, découvert
financé, remboursement ou assurance n'est créé implicitement. `needsFinancing` et
`financingNeed` signalent le cash négatif. `LifeEvent.Financing` est la seule règle d'emprunt
explicite ; aucun échéancier n'est inventé. L'intérêt annuel de dette vaut 0 par défaut,
à fournir explicitement si le contrat en prévoit. Il porte sur le solde d'ouverture.
Rendements illustratifs : prudent 1 %, équilibré 3 %, dynamique 5 % annuels effectifs.

## Études et scénarios

24 mois d'études à effectuer. Frais de base : 800 CHF aux progressions académiques 0,
6, 12 et 18, indexés à leur mois de paiement. Ce sont quatre factures, pas une mensualité.
Le dernier jalon payé est conservé. Aucun paiement pendant une suspension, aucun remboursement
ni facture répétée à la reprise. Cette convention simplifiée peut différer des règles
d'une vraie institution. Le montant, le nombre de mois d'études et la cadence sont configurables.

Après les études : salaire mensuel initial nominal de 6 000 CHF, fixé au premier mois
d'emploi même si décalé. Hausse nominale par paliers de 2 % à chaque anniversaire de carrière,
sans inflation supplémentaire. Le moteur suit explicitement la date de début de carrière.

**S005** : `LifeEvent.Internship`, 12 mois et 2 500 CHF/mois par défaut. Par défaut, décision
accepter/refuser ; `interactive = false` représente un stage déjà décidé. Compatible seulement
avec le statut étudiant et des études restantes. Aucun revenu étudiant ou frais universitaire
simultané. Un stage en m=3 conserve 3 mois validés, suspend 12 mois, reprend les 21 mois restants
en m=15 et conduit au premier emploi en m=36. Une offre devenue incompatible est marquée
`NOT_APPLICABLE`, sans tirage de remplacement.

**S037** : `LifeEvent.RentRise` et `MonthlyRentRule`. Hausse supplémentaire de 3 % dès le mois
configuré, puis tous les 12 mois, jamais tous les mois. Elle s'ajoute à l'inflation générale :
le loyer de référence est stable en pouvoir d'achat. La règle est liée à un contrat identifié.
Une seconde règle active sur le même contrat est explicitement non applicable.

**S040** : `LifeEvent.Maintenance`. Paiement unique de 6 000 CHF nominaux au mois choisi,
sans seconde inflation. `PaymentResponsibility` est obligatoire : `CHARACTER` ou `OTHER_PARTY`.
Ce paramètre n'est jamais inféré du statut de locataire. La réparation n'est pas forcément
à la charge d'Alexia ; elle ne l'est que si cette convention est explicitement sélectionnée.
Les frais habituels restent dans les dépenses récurrentes. Aucun financement automatique.

Les scénarios peuvent être combinés dans le même calendrier et passent par le même moteur.
Des identifiants d'événement uniques évitent le double traitement ; deux événements distincts
représentent deux interventions explicitement planifiées.

## Événements internes et logement

`MicroEvents.plant` : acheter (35 CHF par défaut) ou ne pas acheter (0).
`MicroEvents.oven` : réparer (450) ou remplacer (800), responsabilité explicite.
`MicroEvents.transport` et `equipment` : dépenses ponctuelles de montant explicite.
`LifeEvent.PurchaseChoice` permet de configurer titres, options et montants.
Ces événements sont internes à FinLife, sans identifiant officiel UBS.

`LifeEvent.HousingChoice` propose conserver/changer de logement. Le nouveau loyer nominal
et les frais de déménagement sont explicites ; le nouveau contrat entre en vigueur au mois
de la décision. Son `priceMonth` est ce mois : on ne lui applique pas l'inflation passée.
Il est ensuite indexé. L'ancienne règle S037 est supprimée ; seule une règle explicitement
liée au nouveau contrat peut s'appliquer. Les factures passées restent inchangées.
Pour une décision future, planifier l'événement au mois d'entrée en vigueur ; la V2 ne
modélise pas de préavis avec réponse anticipée. Aucun intitulé seul/colocation n'est inventé.
À mois égal : logement, puis règle de loyer, puis autres événements dans l'ordre des IDs.

## Aléatoire et sessions

Au démarrage, `EventCalendar.generate` produit un calendrier conservé dans la session.
Générateur xorshift32 explicite à graine Long (repliée sur 32 bits) pour un comportement
stable entre plateformes ; ce générateur de démonstration n'est pas cryptographique.
Deux opportunités par bloc de six mois, chacune avec probabilité illustrative 0,4 :
une dépense avec choix (plante ou four si responsabilité four définie), puis une décision
de vie (autre contrat si fourni, sinon stage si études compatibles). Les collisions peuvent
réduire le nombre d'événements. Les événements générés n'occupent pas de mois déjà planifié
et n'ajoutent pas un troisième moment interactif dans une fenêtre de six mois.
Un calendrier explicite de six mois contenant trois moments interactifs est rejeté.
Les parcours longs permettent plus de moments ; les calendriers manuels sont explicites.
Aucun événement n'est forcé. `enabled = false` désactive la génération.

`advance` retourne `AWAITING_DECISION` sans aucune transition si une réponse manque.
`submit` enregistre une option valide sans avancer le mois. Il faut ensuite appeler
`advance` ou `runUntilBlocked`. Plusieurs décisions dans un mois doivent toutes être résolues.
Les événements non applicables sont marqués comme tels. Les coûts ne sont appliqués qu'une
fois la transition complète exécutée ; `inspect` est une prévisualisation pure, sans mutation.
`READY` signifie que l'appel d'avancement peut être demandé ; il peut découvrir une décision
à résoudre. `pendingDecisions` permet de la consulter immédiatement, même avant cet appel.

`viewState` consulte sans recalcul ni tirage. `fork` garde les états jusqu'à l'état de
bifurcation inclus, conserve le calendrier extérieur, efface les réponses à partir de ce
mois et permet des interventions supplémentaires non rétroactives. La branche initiale
reste intacte. Les paramètres/configuration restent communs ; les choix divergent.
Les collections exposées sont des copies, et les objets contenus sont immuables.
Le moteur ne stocke pas un registre global des branches : l'appelant conserve les sessions
et fournit des identifiants de branches distincts dans son application.

## API et exemples compilés dans commonTest

```kotlin
import com.tuna.finlife.simulation.*

val profile = MonthlyProfile.alexia()
val config = MonthlyConfiguration(
    horizonMonths = 6,
    annualInflation = 0.0,
    randomEvents = RandomEventSettings(enabled = false),
)
var session = FinancialSimulation.startSession(profile, config,
    events = listOf(MicroEvents.plant("plant", month = 2)))
session = SimulationSessions.runUntilBlocked(session) // attend au mois 2
val options = SimulationSessions.pendingDecisions(session).single().options
session = SimulationSessions.submit(session, "plant", "first")
session = SimulationSessions.advance(session) // applique le mois 2
val monthResult = session.history.last()
val explanations = monthResult.explanation()
session = SimulationSessions.runUntilBlocked(session) // finit au mois 6

val past = SimulationSessions.viewState(session, 2)
var alternative = SimulationSessions.fork(session, 2, "no-plant")
alternative = SimulationSessions.submit(alternative, "plant", "second")
alternative = SimulationSessions.runUntilBlocked(alternative)
val difference = MonthlyComparisons.compare(session, alternative, month = 6)
val summaries = MonthlyComparisons.annualSummaries(session)
```

`session.currentMonth`, `currentState`, `states`, `history`, `calendar`, `selections`, `status`
et `goal` sont directement utilisables par les interfaces. `currentEvents` renvoie les
événements du mois courant. `MonthlySimulation.calculateMonth(profile, config, state,
events, decisions)` calcule une transition isolée à partir de toutes ses règles explicites.

Exemple de scénario déjà choisi, sans réponse interactive :

```kotlin
val config = MonthlyConfiguration(horizonMonths = 48,
    randomEvents = RandomEventSettings(enabled = false))
val events = listOf(
    LifeEvent.Internship("S005", month = 3, interactive = false),
    LifeEvent.RentRise("S037", month = 24, rule = MonthlyRentRule("initial-home", 24)),
    LifeEvent.Maintenance("S040", month = 40, responsibility = PaymentResponsibility.CHARACTER),
)
val result = SimulationSessions.runUntilBlocked(FinancialSimulation.startSession(configuration = config, events = events))
```

Les comparaisons utilisent une date commune effectivement simulée, le même `profileId`
et le même taux d'inflation pour comparer des CHF réels de même base. Elles retournent les
écarts de cash, revenus et dépenses cumulés, investissements, versements cumulés, patrimoine
nominal et réel. Les effets individuels ne sont jamais supposés additifs. Les agrégations
annuelles incluent un indicateur `isFullYear` pour un horizon partiel.

## Exemples chiffrés vérifiés

Sans inflation, sans investissement et sans événement :

| Transition | Revenu | Dépenses récurrentes | Études | Cash final |
|---|---:|---:|---:|---:|
| m=0 → 1 | 1 200 | 1 400 | 800 | 7 000 |
| m=1 → 2 | 1 200 | 1 400 | 0 | 6 800 |
| m=2 → 3 | 1 200 | 1 400 | 0 | 6 600 |
| m=3 → 4 | 1 200 | 1 400 | 0 | 6 400 |
| m=4 → 5 | 1 200 | 1 400 | 0 | 6 200 |
| m=5 → 6 | 1 200 | 1 400 | 0 | 6 000 |

Avec un four à la charge d'Alexia en m=4 et une réparation choisie à 450 CHF : cash final
à six mois **5 550 CHF**. Avec la plante à m=2 : acheter mène à **5 965 CHF**, refuser
à **6 000 CHF** ; écart de patrimoine **−35 CHF**, états jusqu'à m=2 inclus identiques.
Ces chiffres sont des résultats de tests, pas des données intégrées dans l'affichage.

## Tests et limites

Depuis `FinLife` :

```sh
./gradlew :shared:jvmTest :shared:testAndroidHostTest --offline --console=plain
```

Résultat réellement obtenu après correction : **BUILD SUCCESSFUL**, 58 tests par cible
(JVM et Android hôte), zéro échec, zéro erreur et zéro test ignoré. Ces 58 tests comprennent
32 nouveaux tests V2, 24 tests V1 du moteur et 2 tests préexistants du module.

Tests V1 conservés. Tests V2 : conditions initiales exactes, N+1 états, échéances académiques,
stage et reprise, carrière, hausse annuelle du loyer, responsabilité des réparations, huit
combinaisons, contrats de logement, blocage des décisions, plafonnement des placements,
réconciliation au centime, taux effectifs, historique immuable, branches, calendrier reproductible,
comparaison et agrégation. Les tests Android sont des tests hôte, pas des tests sur appareil.
Le premier passage V2 a détecté un cas d'arrondi de demi-centime lié aux Double ; corrigé
et couvert par une régression (`Money(100).scaled(1.005) == Money(101)`).

Pas d'interface, importateur CSV, sérialisation/disque, fiscalité, garantie, assurance événementielle,
financement automatique, anticipation du marché ou probabilités empiriques. Les règles universitaires,
les contrats et les revenus sont volontairement simplifiés. Les taux négatifs > −100 % sont acceptés
pour inflation/rendement/croissance, mais pas de rendement annuel exactement −100 % dans cette version.
Les deux cibles actuelles Android/JVM sont vérifiées ; aucun résultat de test iOS/JS n'est revendiqué.

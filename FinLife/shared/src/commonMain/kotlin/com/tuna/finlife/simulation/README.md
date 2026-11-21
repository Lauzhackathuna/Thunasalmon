# FinLife V3 — moteur mensuel, enveloppes et projections

Package commun Kotlin `com.tuna.finlife.simulation`, sans Compose, réseau, horloge ni état global.
Les sessions et les états sont des valeurs immuables ; inspecter, projeter ou bifurquer ne modifie
jamais la branche source. Les collections publiques sont des copies. Les calculs V3 prolongent
`MonthlySimulation`, `Money`, `EventCalendar`, les décisions et la progression académique V2.
Les API annuelles V1 (`FinancialSimulation.simulate`, etc.) restent disponibles avec leurs anciennes
hypothèses, uniquement pour compatibilité. Elles ne calculent pas les projections V3.

Les montants du profil et les paramètres sont des hypothèses FinLife configurables, pas des données
empiriques UBS. Les définitions `ScenarioCatalog` S005, S037 et S040 sont conservées. Les conventions
mathématiques sont celles du moteur existant : flux nominaux, intérêts sur dette d'ouverture,
rendement sur placements d'ouverture, versements investis en fin de période, patrimoine réel déflaté.
La distinction entre liquidités et dettes exigibles étend ces conventions sans richesse créée.

## Profil, temps et précision

`MonthlyProfile.alexia()` : Alexia, 20 ans, Lausanne, STUDENT, ALONE, 8 000 CHF de cash,
aucun investissement ni emprunt. Revenu étudiant nominal de 1 200 CHF/mois ; dépenses de référence :
loyer 1 000, assurance 250, courses 250, autres 300. Les études durent 24 mois académiques.
Les factures universitaires de référence sont de 800 CHF aux progressions 0, 6, 12 et 18.

L'état m ouvre le mois m ; les décisions et les paiements de m conduisent à l'état m+1.
N transitions donnent N+1 états. Les échéances académiques sont suspendues pendant S005 et reprennent
au même jalon, sans double facturation. Une facture impayée n'empêche pas la progression académique.
Le champ historique `lastPaidTuitionProgress` est conservé pour compatibilité : il désigne désormais
le **dernier jalon facturé**, également accessible par `lastAssessedTuitionProgress`. La preuve de
paiement se trouve exclusivement dans `budget.payments`, et le solde dû dans `state.unpaid`.

`Money` utilise des centimes Long, sommes exactes, arrondi des lignes au centime (demi-centime vers
l'extérieur de zéro). Les taux sont en Double. Pas d'arrondi supplémentaire des cumuls.
Inflation par défaut : 2 % ; indice `P(m) = (1 + inflation)^(m/12.0)`.
Taux mensuel effectif d'intérêt/rendement : `(1 + tauxAnnuel)^(1/12.0) - 1`.
Le patrimoine réel de l'état m est le patrimoine nominal divisé par P(m), arrondi au centime.
Les limites numériques et les validations de taux V2 restent applicables.

## Enveloppes et prévision du budget

| Enveloppe | CHF initiaux |
|---|---:|
| HOUSING | 3 000 |
| INSURANCE | 1 000 |
| GROCERIES | 750 |
| STUDIES | 1 600 |
| OTHER | 300 |
| RESERVE | 1 350 |
| Total | 8 000 |

`state.envelopes[Envelope.HOUSING]` et les propriétés nommées donnent les soldes.
Invariant vérifié : `state.envelopes.total == state.cash`, avec tous les soldes non négatifs.
`MonthlyProfile.initialEnvelopes` accepte une autre affectation dont le total doit égaler le cash.
Sans affectation explicite : 8 000 CHF utilisent cette répartition de référence ; tout autre montant
est initialement en RESERVE. Aucun montant n'est ajouté aux liquidités, investissements ou dettes.
Les cash initiaux négatifs V2 sont désormais rejetés : fournir un état avec passifs explicites.

Le revenu reçu comble les besoins non encore financés dans l'ordre :
**STUDIES, HOUSING, INSURANCE, GROCERIES, OTHER, RESERVE**. La réserve reçoit le surplus.
Un besoin déjà couvert ne reçoit rien. Un emprunt explicitement demandé est crédité en RESERVE,
séparément des allocations de revenu ; il n'est jamais un revenu.

La politique prévisionnelle V3 est explicite : les dépenses récurrentes du mois, les deux prochaines
échéances académiques restantes (y compris celle du mois), et les dépenses exceptionnelles certaines
connues des douze prochains mois. Les factures d'études sont indexées à leur date prévisionnelle ;
les suspensions S005 actives ou futures déjà engagées décalent ces dates. Les montants exceptionnels
sont nominaux, sans seconde inflation. Les choix encore sans réponse ne sont pas présumés acceptés.
Les objectifs sont recalculés chaque mois après les décisions : logement effectif, inflation,
progression académique, revenu disponible dont le mini-stage. Le revenu temporaire ne change pas
la priorité et n'est pas présumé disponible après sa fin. Les études provisionnées ne sont pas dépensées.
Un besoin sur deux échéances est une politique FinLife configurable par les paramètres académiques,
non une obligation universelle de garder exactement deux factures en réserve.

Pour une transition isolée, passer `knownFutureEvents` à `calculateMonth` pour provisionner les
factures connues. Les sessions fournissent leur calendrier futur ; les projections fournissent
uniquement le calendrier explicitement retenu. Connaître une future facture peut donc modifier
les enveloppes avant sa date, sans diminuer le cash ni le patrimoine.

## Paiements, transferts, passifs et explications

Les obligations sont réglées par date d'exigibilité (arriérés d'abord), puis catégorie prioritaire,
puis ID déterministe. Un manque dans une enveloppe prélève **OTHER, RESERVE, GROCERIES, INSURANCE,
HOUSING, STUDIES**, en excluant l'enveloppe destinataire. Un transfert n'est ni un revenu ni une dépense.
`EnvelopeTransfer` conserve source, destination, montant, facture, raison et provision exposée
`futureFundingAtRisk`. Ce dernier est un avertissement prudent sur les cibles budgétaires, pas une
prédiction certaine d'impayé. Les cibles comprennent aussi les obligations pertinentes du mois.

Exemple : loyer 1 000, HOUSING 850, OTHER 100 : transfert OTHER → HOUSING de 100,
puis RESERVE → HOUSING de 50. Le paiement fait baisser le cash de 1 000, une seule fois.
Pour un imprévu de 500 avec OTHER 300 et RESERVE 1 350 : transfert de 200 depuis RESERVE,
paiement de 500 depuis OTHER, réserve finale 1 150.

Le registre `MonthlyRecord.budget` expose `before`, `after`, `allocations`, `transfers`, `payments`,
`outstanding`, `borrowing`, `invested`, `paidExpenses` et `paidPrincipal`.
`FinancialObligation` contient ID, date, catégorie, montant initial, cumul payé et `missing`.
Les frais événementiels portent l'ID de leur événement, même en cas de paiement partiel.
`record.explanation()` compose ses textes depuis ces opérations et les résultats réels du moteur.
`EventOutcome.chargedCost` est le coût **exigible**, pas la preuve d'un règlement intégral.

Si les fonds manquent, le paiement est limité au disponible, l'enveloppe ne devient jamais négative,
et l'obligation restante persiste dans `state.unpaid.items`. Aucun prêt ou actif vendu automatiquement.
Les arriérés sont retentés les mois suivants, sans nouvelle dépense comptable ni intérêts de retard
inventés. Un remboursement de capital impayé reste dans la dette bancaire ; il est identifié comme
`bankPrincipal` et n'est pas ajouté une seconde fois aux dettes fournisseurs.

Contrats publics conservés, avec clarification des flux :

- `flow.expenses` : charges exigibles du mois (comptabilité d'engagement).
- `flow.paidExpenses` / `record.paidExpenses` : sorties effectivement payées, y compris anciens arriérés.
- `flow.principalRepayment` : capital effectivement remboursé.
- `flow.netCashFlow = income + newBorrowing - paidExpenses - principalRepayment`.
- `cash' = cash + netCashFlow - investmentContribution`.
- `investments' = investments + investmentReturn + investmentContribution`.
- `debt' = debt + newBorrowing - principalRepayment` (emprunts bancaires seulement).
- `accountsPayable` : somme des obligations impayées hors capital bancaire.
- `netWorth = cash + investments - debt - accountsPayable`.
- `netWorthChange = income - expenses + investmentReturn`.
- `theoreticalCash = cash - somme(des obligations impayées)` : indicateur, jamais de l'argent disponible.

Les anciens comparateurs et agrégats annuels gardent `expenses` et ajoutent `paidExpenses`.
`needsFinancing` / `financingNeed` indiquent les obligations encore non couvertes.
Le versement investi est plafonné par le surplus d'exploitation après arriérés, le cash hors nouvel
emprunt et la RESERVE disponible après tous les paiements. Les affectations futures ne sont pas investies.

## Logement et stages

`SimulationSessions.proposeSharedHousing(session)` ajoute une décision au mois courant, sans avancer.
Répondre `move` ou `stay`, puis avancer. La colocation est accessible sans S037 et indisponible si déjà
active. Pour une date future, utiliser `schedule` avec `MonthlySimulation.sharedHousingChoice`.
Le nouveau contrat supprime la règle S037 de l'ancien. `state.housingDecision` conserve date, motif et ID ;
l'historique contient toutes les décisions. Aucun frais fictif : `sharedMovingCost = 0` par défaut.

`sharedReferenceRent = 800` est exprimé aux prix du mois zéro. À la signature m, il est indexé une fois,
arrondi au centime et devient le loyer nominal du contrat à `priceMonth=m`. Ensuite, seule l'inflation
relative à m s'applique. Un contrat explicite `HousingContract` utilise directement son prix nominal
à `priceMonth` ; il ne réutilise aucun prix ni supplément d'un ancien contrat.

Une hausse contractuelle S037 positive, applicable au logement actuel non partagé, bloque désormais
la simulation : `stay` accepte la hausse, `move` prend le nouveau contrat partagé. La hausse supplémentaire
s'applique dès sa date puis à chaque anniversaire de douze mois, en plus de l'indice général.
L'inflation générale ne déclenche aucun choix. Une hausse de l'ancien contrat est non applicable après
le déménagement. Une hausse du contrat partagé actuel s'applique sans reproposer la colocation.

`LifeEvent.ShortInternshipOffer(id="FINLIFE_INTERNSHIP_OFFER", month, startMonth=month,
monthlyIncome=Money.chf(300))` est interne, sans `ScenarioId` UBS. Accepter (`accept`) ajoute exactement
300 CHF pendant `[startMonth, startMonth+3[`, soit 900 CHF. Refuser (`decline`) ajoute zéro.
Le revenu étudiant et le calendrier académique continuent. Une offre acceptée persiste dans l'état,
même si son début est futur. Elle ne s'indexe pas automatiquement et n'est pas un revenu permanent.

S005 reste `LifeEvent.Internship` : 12 mois, 2 500 CHF/mois par défaut, remplacement du revenu étudiant
et suspension académique. Une offre de mini-stage exige le statut étudiant, sans autre mini-stage
engagé ni S005 actif. Un S005 déjà engagé dans le calendrier prime sur une nouvelle offre incompatible.
Un mini-stage déjà accepté rend une nouvelle proposition S005 incompatible jusqu'à sa fin.
Les événements incompatibles sont `NOT_APPLICABLE`, pas silencieusement remplacés. Une proposition
interactive future n'est pas un engagement tant qu'elle n'a pas été acceptée.

## API de session et exemple d'intégration

```kotlin
val config = MonthlyConfiguration(randomEvents = RandomEventSettings(enabled = false))
var session = SimulationSessions.start(configuration = config)
session = SimulationSessions.proposeSharedHousing(session)
val housing = SimulationSessions.pendingDecisions(session).single()
session = SimulationSessions.submit(session, housing.event.id, "move")
session = SimulationSessions.schedule(session, LifeEvent.ShortInternshipOffer(month = 0))
session = SimulationSessions.submit(session, "FINLIFE_INTERNSHIP_OFFER", "accept")
session = SimulationSessions.advance(session)
val envelopes = session.currentState.envelopes
val transfers = session.history.last().budget.transfers
val explanations = session.history.last().explanation()
val projection = FinancialProjections.nothingChanges(session)
val comparison = FinancialProjections.compareDecisions(
    session.profile, session.configuration, session.currentState)
val stability = FinancialStabilityAnalysis.evaluate(
    session.profile, session.configuration, comparison.reference, comparison)
val curves = comparison.curves // rôle, libellé, couleur indicative, points, projection complète
```

`inspect`, `currentEvents`, `pendingDecisions`, `submit`, `advance`, `runUntilBlocked`, `viewState`,
`fork`, `history`, `states`, `selections`, `goal` restent disponibles. `submit` ne fait pas avancer le
mois ; toutes les décisions applicables doivent être résolues. `fork` conserve calendrier et préfixe,
efface les réponses depuis la bifurcation et ne retire aucun événement extérieur.
La limite de deux opportunités aléatoires par six mois reste dans le générateur déterministe.
Elle ne limite plus les décisions explicitement planifiées : une colocation volontaire reste accessible.

## Douze mois, scénarios et données de courbe

`FinancialProjections.nothingChanges(profile, configuration, state, knownEvents, commitments)`
part de n'importe quel état valide et calcule exactement douze transitions, treize états.
L'horizon de calcul est prolongé si nécessaire, sans modifier la session (limite globale : mois 1 200).
Aucun générateur aléatoire n'est appelé. La surcharge prenant une session retient par défaut ses
événements explicites futurs (IDs hors préfixe réservé `random-`) et ses décisions déjà soumises.
Un calendrier `knownEvents` explicite remplace cette sélection si l'interface connaît d'autres événements.
Les événements déjà actifs restent intégralement dans l'état.

Sans engagement explicite : conserver le logement, refuser les nouvelles offres, ignorer les achats
discrétionnaires non choisis. Les contrats actifs, frais universitaires, revenus réguliers, impayés,
inflation et événements extérieurs déjà connus continuent. Chaque projection expose ses hypothèses,
les registres, les dépenses payées, les enveloppes, le patrimoine nominal/réel et les mois déficitaires.
`project(label, ...)` permet une projection avec choix explicites, notamment un stress séparé.

`compareDecisions(profile, config, state, knownEvents, offer)` explore un ensemble fini : conserver
le logement ou partager immédiatement, et accepter/refuser l'offre courte fournie. Sans offre : deux
possibilités ; déjà en colocation : pas de second déménagement. Les alternatives inapplicables sont
exclues. Les autres décisions extérieures gardent leurs réponses de référence. Le calendrier extérieur
est identique ; un effet peut devenir non applicable après une décision (ex. ancien contrat S037).
Ne pas passer de choix de logement/achat/mini-stage dans `knownEvents` de cette comparaison ; utiliser
`offer` pour la proposition commune. Aucun S040 supplémentaire n'est ajouté pour fabriquer un pire cas.
Un stress fourni explicitement est commun à toutes ces trajectoires et figure dans leurs hypothèses.

Le classement utilise le cash **disponible au terme**, puis moins d'impayés en cas d'égalité, puis plus
de patrimoine. Les courbes sont les trajectoires entières sélectionnées, pas des maximums/minimums
fabriqués chaque mois. Elles peuvent se croiser. `ProjectionCurve.points` fournit date, cash, cash
théorique, patrimoine nominal/réel, impayés et loyer du mois qui vient de se terminer (`null` à l'origine).
`role`, `label` et `colorHex` fournissent une légende neutre/verte/rouge indépendante des couleurs.
Aucune bibliothèque graphique n'est imposée. Il s'agit des cas étudiés, pas de tous les futurs,
ni de prévisions certaines ou d'intervalles de confiance.

## Stabilité et sensibilité distinctes

`averageSpread = somme(cashBest(m) - cashWorst(m), m=1..12) / 12`, arrondi au centime, en CHF.
C'est l'écart signé des deux trajectoires sélectionnées au terme ; des croisements peuvent le réduire.
`relativeSpread` divise cet écart par la moyenne des dépenses essentielles des douze mois de la projection analysée.
Aucune probabilité n'est attribuée à la dispersion et aucun score ne mélange CHF et ratios.

`FinancialStabilityAnalysis.evaluate` analyse la projection choisie, indépendamment de la dispersion :

- Couverture à chaque ouverture et à la clôture : loyer + assurance + courses + frais d'études lissés
  (`tuitionPayment / tuitionEveryStudyMonths`, indexés), plus la fraction configurable d'OTHER.
  La provision analytique ne remplace jamais les factures effectives. Après les études elle vaut zéro.
- RESERVE, liquidités mobilisables, cash non affecté net des dettes fournisseurs, mois couverts et
  repères de trois/six mois sont séparés. Si les dépenses essentielles sont nulles, le ratio vaut `null`
  (pas de besoin mesurable), jamais NaN/infini. Mobiliser les enveloppes peut exposer des factures futures.
- Budget structurel : revenu étudiant régulier ou salaire − dépenses récurrentes − provision d'études
  − intérêts bancaires. Ni S005 ni mini-stage ne sont permanents. Tolérance d'équilibre : ±10 CHF par défaut.
- Résilience : mois avec impayés, maximum du financement manquant, impayés au terme, cash minimum,
  épuisement de RESERVE, capacité brute à absorber `unexpectedExpense` (500 CHF par défaut) après impayés.
  La capacité brute n'est pas une promesse de préserver toutes les affectations futures ; le cash non
  affecté est affiché séparément.

Classement déterministe, conditions testées dans cet ordre :

1. **CRITICAL** : besoin maximal supérieur à `significantUnpaid` (100 CHF par défaut), ou au moins deux
   clôtures mensuelles avec impayés.
2. **FRAGILE** : au moins une clôture avec impayés, ou budget structurel déficitaire à une date,
   ou couverture inférieure à un mois.
3. **WATCH** : couverture inférieure à trois mois à une date, ou imprévu configurable non absorbable.
4. **STABLE** : aucun de ces critères défavorables.

Les six mois sont affichés comme repère supplémentaire, pas comme seuil d'une note cachée. Trois à six
mois est un repère prudent de finances personnelles évoqué dans le besoin produit, pas une règle de
notation UBS ni un seuil universel. Les paramètres et raisons du classement sont exposés.
Indicateur pédagogique de simulation FinLife, ni notation officielle UBS ni conseil personnalisé.

## Exemples vérifiés

Zéro inflation, aucune intervention extérieure, aucune contribution investie :

| Mois terminé | Cash disponible | Impayés |
|---|---:|---:|
| 0 (initial) | 8 000 | 0 |
| 1 | 6 600 | 0 |
| 2 | 6 000 | 0 |
| 3 | 5 400 | 0 |
| 4 | 4 800 | 0 |
| 5 | 4 200 | 0 |
| 6 | 3 600 | 0 |
| 7 | 2 200 | 0 |
| 8 | 1 600 | 0 |
| 9 | 1 000 | 0 |
| 10 | 400 | 0 |
| 11 | 0 | 200 |
| 12 | 0 | 800 |

Revenus 14 400 ; charges récurrentes 21 600 ; études 1 600 ; flux théorique −8 800.
Dépenses effectivement payées : 22 400. Cash théorique final −800, cash disponible 0,
passifs fournisseurs 800, dette bancaire 0, patrimoine nominal/réel −800.
Avec inflation annuelle 2 % : cash théorique −1 005,28, passifs 1 005,28,
patrimoine réel −985,57 CHF. Ces chiffres sont des assertions des tests, pas des constantes d'affichage.

Décisions au mois zéro, zéro inflation :

| Logement | Offre courte | Cash après 12 mois | Impayés | Patrimoine |
|---|---|---:|---:|---:|
| Seule, 1 000/mois | Refusée | 0 | 800 | −800 |
| Seule, 1 000/mois | Acceptée | 100 | 0 | 100 |
| Colocation, 800/mois | Refusée | 1 600 | 0 | 1 600 |
| Colocation, 800/mois | Acceptée | 2 500 | 0 | 2 500 |

Le stage apporte exactement 900 CHF ; la colocation économise 2 400 CHF sur les douze mois.
L'écart moyen meilleur/pire de cet ensemble vaut 2 041,67 CHF. Les deux trajectoires sont encore
structurellement déficitaires. Deux copies du scénario seul/refusé ont un écart nul et restent CRITICAL.
Essentiels seuls : 1 633,33 CHF/mois ; colocation : 1 433,33. Repères seuls : environ 4 900 / 9 800.

## Tests et limites

Depuis `FinLife` : `./gradlew :shared:jvmTest :shared:testAndroidHostTest --offline --console=plain`.
Validation exécutée : **82 tests JVM et 82 tests Android hôte**, zéro échec, erreur ou test ignoré.
Les tests communs couvrent les invariants, arrondis, transferts et priorités, arriérés/règlement,
capital bancaire, progression académique, logement, stages, indexation, projections, scénarios,
stabilité et reproductibilité. Les tests annuels V1 sont conservés. Android est testé sur hôte,
pas sur appareil ; aucune validation iOS/JS n'est revendiquée.

Simplifications : paiements mensuels sans calendrier civil/journalier, pas de fiscalité ou de pénalités
de retard, prix et revenus déterministes, absence de probabilités empiriques, réserve budgétaire selon
une politique explicite de deux échéances académiques, pas d'optimiseur global des choix futurs.
Les cibles n'anticipent pas l'acceptation d'une proposition encore non décidée. Une nouvelle information
recalcule le budget au prochain mois. La colocation ne comporte ni préavis ni coût par défaut.
Le moteur ne certifie pas la solvabilité réelle ni la conformité de chaque hypothèse à un contrat UBS.

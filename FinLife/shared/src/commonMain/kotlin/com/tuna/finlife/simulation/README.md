# Moteur partagé FinLife V1

API publique : `com.tuna.finlife.simulation`. Aucun import d'interface, accès réseau,
horloge, persistance ou aléatoire. Les fonctions recalculent entièrement les trajectoires
sans modifier leurs entrées. Ne pas modifier simultanément les listes fournies pendant un appel.

## Origine et portée

Les métadonnées UBS de `scenario-library-120.csv` sont retranscrites selon les définitions
fournies par l'équipe ; le CSV n'est pas encore local. **Toutes les valeurs numériques sont
les hypothèses de notre équipe, pas des chiffres officiels UBS.** Ces spécifications
prévalent sur l'ancien `docs/modele.md`, laissé intact. Seuls S005, S037 et S040 sont actifs.

| ID | Catégorie officielle | Scénario | Référence | Intervention |
|---|---|---|---|---|
| S005 | Education & skills | Internship year | Continue studies | Paid internship year |
| S037 | Housing & property | Rent increase | Stable rent | Annual rent rises |
| S040 | Housing & property | Unexpected maintenance | Normal upkeep | Major repair bill |

## Hypothèses et calendrier

Alexia, 20 ans, Lausanne : cash 8 000 CHF, investissements et dette nuls. Objectif nominal
350 000 CHF à t=10. Dix années de flux (t=0..9) produisent onze états (t=0..10).
L'état t précède les flux t ; la réparation de t=4 affecte donc l'état t=5.

Sans S005 : études à t=0 et t=1, revenus nets 12 000 CHF/an, premier salaire de
72 000 CHF à t=2. Avec S005 : stage de 30 000 CHF nets à t=0, études à t=1 et t=2,
revenus étudiants 12 000 CHF/an, premier salaire de 72 000 CHF à t=3. Le stage
remplace le revenu étudiant, ne s'y ajoute pas, et ne supporte pas de frais d'études.
La croissance professionnelle de 2 %/an est déjà nominale, sans seconde inflation.
Frais d'études : 1 500 CHF initiaux indexés, uniquement pendant les études.

Inflation par défaut 2 %, configurable dans `FinancialAssumptions` : elle indexe le loyer
initial de 9 600 CHF, les dépenses essentielles de 12 000 CHF, discrétionnaires de
6 000 CHF et les frais d'études. Tous les montants calculés sont nominaux.
Sans S037, loyer(t) = 9 600 × (1 + inflation)^t. Avec S037, multiplier encore par
1,03^(t−1) dès t=2 inclus. Cette hausse supplémentaire est récurrente et composée ;
son taux et son année de début sont configurables dans `RentIncrease`.
Patrimoine réel(t) = patrimoine nominal(t) / (1 + inflation)^t, exprimé en CHF de t=0.
L'objectif et son pourcentage restent nominaux.

S040 : paiement exceptionnel unique de 6 000 CHF nominaux à t=4. Montant et année
configurables, sans nouvelle indexation. L'année doit appartenir à l'horizon si activé.
**Convention : la réparation est supposée être à la charge d'Alexia. Ce n'est pas une règle
selon laquelle toute grosse réparation incombe au locataire.** L'entretien habituel
reste dans les dépenses normales. Aucun emprunt ni financement automatique.

## Calcul financier

Conventions conservées : part du surplus investie 0 % par défaut ; rendement 3 % ;
intérêt de dette 6 % sur le solde d'ouverture. Ces paramètres sont configurables.

- Dépenses = dépenses normales (dont études) + dépenses ponctuelles + intérêts.
- Flux net = revenus + emprunts explicites − dépenses − capital remboursé.
- Versement investi = part investie × max(flux net, 0).
- Cash suivant = cash + flux net − versement investi.
- Investissements suivants = investissements × (1 + rendement) + versement investi.
- Dette suivante = dette + emprunts explicites − capital remboursé.
- Patrimoine net = cash + investissements − dette.

Versements investis en fin d'année. Un cash négatif déclenche `needsFinancing` ; il
n'entraîne ni prêt, ni intérêt de découvert, ni liquidation automatique des investissements.
La règle d'investissement reste basée sur le flux annuel positif, même si le cash cumulé
est encore négatif. Le capital remboursé n'est pas une dépense. La référence a déjà un
déficit : à la fin de t=0, cash = 8 000 + 12 000 − 29 100 = −9 100 CHF.
Calculs en Double sans arrondis intermédiaires ; l'interface décide de l'affichage.

`breakdown` détaille les dépenses normales : ne pas les additionner à nouveau.
`maintenanceExpense` est déjà inclus dans `oneOffExpenses`. Les plans annuels personnalisés
sont nominaux et remplacent le parcours généré ; ils ne peuvent pas être combinés avec
S005/S037 activés (rejet explicite pour éviter de masquer leur effet). S040 reste applicable.
Leur longueur détermine l'horizon. Pour les usages V1, laisser `annualPlans = null`.

## API pour Android et desktop

```kotlin
import com.tuna.finlife.simulation.*

val parameters = SimulationParameters(
    internship = true,
    rentIncrease = RentIncrease(enabled = true),
    maintenance = UnexpectedMaintenance(enabled = true, paymentYear = 4, nominalAmount = 6_000.0),
    assumptions = FinancialAssumptions(inflation = 0.02),
)
val result = FinancialSimulation.simulate(parameters)
val nominal = result.finalState.netWorth
val real = result.finalState.realNetWorth
val deficit = result.flows.any { it.needsFinancing }
val totalEffect = SimulationComparisons.againstReference(parameters)
val internshipEffect = SimulationComparisons.effect(parameters, ScenarioId.S005)
val rentEffect = SimulationComparisons.effect(parameters, ScenarioId.S037)
val repairEffect = SimulationComparisons.maintenanceEffect(parameters)
val explanations = FinancialExplanations.explain(parameters)
val availableScenarios = ScenarioCatalog.active
```

`effect` retire seulement le scénario demandé, en conservant les autres scénarios,
paramètres économiques et soldes initiaux. Un scénario désactivé a un effet nul.
`againstReference` désactive les trois interventions en conservant les autres paramètres.
`compare` accepte deux configurations de même horizon. Les écarts sont sélection moins
contrefactuel : patrimoine nominal/réel par état et revenus/dépenses par année.
Chaque comparaison recalcule deux trajectoires complètes ; aucun effet individuel n'est
additionné artificiellement. Les combinaisons utilisent les mêmes règles annuelles du moteur.

## Vérification

Tests communs : `shared/src/commonTest/kotlin/com/tuna/finlife/simulation`.
Depuis `FinLife` :

```sh
./gradlew :shared:jvmTest :shared:testAndroidHostTest --offline --console=plain
```

Couverture : huit combinaisons contrôlées par une récurrence indépendante, calendriers
études/stage/emploi, inflation, loyers composés, paiement unique nominal, absence d'emprunt
implicite, déficits, réconciliation, patrimoine réel, comparaisons, reproductibilité et entrées
invalides. Aucun changement des interfaces Android/desktop ou des configurations Gradle.

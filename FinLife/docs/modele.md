> **Archive — modèle annuel V1.** Ce document conserve les anciennes hypothèses et ne décrit pas l’aventure actuelle. Pour le parcours à prénom personnalisable, les enveloppes, les impayés et les projections mensuelles, consulter [le README actuel](../README.md) et [le moteur V3](../shared/src/commonMain/kotlin/com/tuna/finlife/simulation/README.md).

1. Conventions
   Monnaie : CHF nominaux. Pas d'inflation dans la version 1 (limite assumée, affichée dans l'app).
   Pas de temps : 1 an.
   États : t = 0, 1, …, 10 (Alexia a 20 ans à t = 0, 30 ans à t = 10).
   Flux : t = 0, …, 9. Les flux de l'année t font passer de l'état t à l'état t+1.
   Simplification : une voiture achetée n'est pas comptée comme un actif (on suppose qu'elle perd sa valeur).
2. Profil de départ (fixe)
   Alexia, 20 ans, Lausanne.
   C_0 = 8 000 (cash), I_0 = 0 (investissements), D_0 = 0 (dette).
   Objectif G : choisi par l'utilisateur au début (patrimoine net visé à t = 10, en CHF).
3. Variables d'état (pour chaque t)
   C_t : cash
   I_t : investissements
   D_t : dette restante
   NW_t = C_t + I_t − D_t : patrimoine net (c'est « l'argent » affiché et comparé à l'objectif)
4. Questions, réponses possibles et valeurs par défaut

Chaque question a une valeur par défaut. L'ensemble des valeurs par défaut forme le scénario de référence. Une question sans réponse garde sa valeur par défaut.

Catégorie Études

Q1 — Faire un master de 2 ans ? Réponses : non / oui. Défaut : non.

Catégorie Carrière

Q2 — Où travailler ? Réponses : Lausanne / Zurich. Défaut : Lausanne.

Catégorie Logement

Q3 — Type de logement ? Réponses : chez les parents / colocation / studio seul. Défaut : colocation.

Catégorie Dépenses

Q4 — Train de vie (hors loyer), en CHF/an ? Curseur de 12 000 à 30 000, pas de 1 000. Défaut : 18 000.

Catégorie Investissements

Q5 — Part du surplus investie ? Curseur de 0 % à 100 %, pas de 10 %. Défaut : 0 %. Q6 — Profil de placement ? Réponses : prudent / équilibré / dynamique. Défaut : équilibré.

Catégorie Gros achats

Q7 — Acheter une voiture à crédit ? Réponses : non / oui, avec l'année d'achat k (curseur de 0 à 9). Défaut : non.

5. Des réponses aux paramètres de chaque année (PROVISOIRE)
   Salaire net (salaire_t)
   Croissance annuelle des salaires : g = 2 %.
   Sans master (Q1 = non) : salaire_t = 60 000 × (1 + g)^t.
   Avec master (Q1 = oui) :
   t = 0, 1 : salaire_t = 12 000 (job étudiant), etudes_t = 1 500 ;
   t ≥ 2 : salaire_t = 72 000 × (1 + g)^(t − 2).
   Si Q2 = Zurich : le salaire (hors job étudiant) est multiplié par 1,10.
   Loyer (loyer_t)
   Chez les parents : 3 000 / an (participation).
   Colocation : 9 600 / an.
   Studio seul : 15 600 / an.
   Si Q2 = Zurich : loyer multiplié par 1,25 (sauf « chez les parents »).
   Pendant le master, Q2 ne s'applique pas (études à Lausanne) : le multiplicateur Zurich ne s'applique qu'à partir de t = 2.
   Dépenses de vie (vie_t)
   vie_t = loyer_t + train_de_vie (Q4) + frais_voiture_t.
   Investissements
   Part investie : s = valeur de Q5 (entre 0 et 1).
   Rendement annuel r : prudent 1 %, équilibré 3 %, dynamique 5 %. (Version 1 déterministe. Bonus possible : Monte Carlo sur r.)
   Voiture (si Q7 = oui, achat en année k)
   achat_k = 20 000 et emprunt_k = 20 000 (achat financé entièrement par le crédit).
   Remboursement du capital : 4 000 / an pour t = k+1, …, k+5 (en restant ≤ 9 ; ce qui reste dû à t = 10 reste en dette).
   Taux d'intérêt : i_d = 6 % sur la dette restante.
   Frais de voiture : frais_voiture_t = 3 000 / an pour t ≥ k.
   Pour tout autre t : achat_t = 0, emprunt_t = 0, remboursement_t = 0.
6. Règles de passage de t à t+1 (dans cet ordre)
   interets_t = i_d × D_t
   recettes_t = salaire_t
   depenses_t = vie_t + etudes_t + achat_t + interets_t
   F_t = recettes_t + emprunt_t − depenses_t − remboursement_t (flux de cash net)
   V_t = s × max(F_t, 0) (montant investi)
   C_{t+1} = C_t + F_t − V_t
   I_{t+1} = I_t × (1 + r) + V_t
   D_{t+1} = D_t + emprunt_t − remboursement_t
   NW_{t+1} = C_{t+1} + I_{t+1} − D_{t+1}

Si C_{t+1} < 0 : besoinFinancement_t = vrai (on le signale, on ne le cache pas). Le remboursement de capital n'est pas une dépense : il réduit à la fois le cash et la dette.

7. Indicateurs affichés
   Pourcentage de l'objectif à l'année t : P_t = 100 × NW_t / G (peut être négatif ou dépasser 100).
   Projection à 10 ans : NW_10 et P_10 (« si tu continues comme ça »).
   Pour l'année t choisie avec le curseur : recettes_t, depenses_t, D_t (dette restante), NW_t.
8. Effet exclusif d'un choix

Pour une question q, on lance deux simulations :

S = simulation avec toutes les réponses actuelles ;
S_q = la même chose, mais avec q remise à sa valeur par défaut.

Effet de q à l'année t :

ΔNW_q(t) = NW_t(S) − NW_t(S_q)
Δrecettes_q(t) = recettes_t(S) − recettes_t(S_q)
Δdepenses_q(t) = depenses_t(S) − depenses_t(S_q)

Attention : les effets ne s'additionnent pas. La somme des effets individuels n'est en général pas égale à l'écart total avec la référence, parce que les choix interagissent (un salaire plus élevé permet d'investir davantage, et le rendement s'applique à une somme plus grande).

9. Contrat avec l'interface

Entrées :

objectif G ;
réponses : une table questionId → valeur (les questions absentes prennent leur valeur par défaut).

Sorties :

liste des 11 états (t = 0..10) avec : t, âge, cash, investissements, dette, patrimoineNet, recettes, depenses, remboursement, besoinFinancement, pourcentageObjectif (pour t = 10, les flux valent 0) ;
projection : NW_10 et P_10 ;
fonction effetDuChoix(q) qui renvoie ΔNW, Δrecettes, Δdepenses pour chaque t.

La simulation est une fonction pure : mêmes entrées → mêmes sorties, aucun état caché. L'interface la relance entièrement à chaque réponse ou retour à une question précédente.

10. Vérifications (tests automatiques)
    Réconciliation du cash : C_{t+1} − C_t = F_t − V_t pour tout t.
    Patrimoine : NW_t = C_t + I_t − D_t pour tout t.
    Dette : D_{t+1} = D_t + emprunt_t − remboursement_t, et D_t ≥ 0.
    Effet nul : si q est à sa valeur par défaut, ΔNW_q(t) = 0 pour tout t.
    Isolation : changer la réponse à Q7 ne modifie rien avant l'année d'achat k.
    Reproductibilité : deux appels identiques donnent des résultats identiques.
    Calcul à la main (scénario de référence, t = 0 → 1) : recettes_0 = 60 000 ; depenses_0 = 9 600 + 18 000 = 27 600 ; F_0 = 32 400 ; V_0 = 0 ; C_1 = 8 000 + 32 400 = 40 400 ; I_1 = 0 ; D_1 = 0 ; NW_1 = 40 400.


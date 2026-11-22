# FinLife — démo reproductible de cinq minutes

## Préparation

- Construire et vérifier : `bash gradlew :shared:jvmTest :shared:testAndroidHostTest :desktopApp:classes :androidApp:assembleDebug`, puis `python3 tools/check_resources.py`.
- Ouvrir Desktop et un émulateur Android. Saisir un prénom puis **Démo EPFL · accès direct** sur l’écran du budget. Le bouton utilise le budget par défaut, pas les montants modifiés dans ce formulaire.
- La graine 19 rejoue le parcours normal : 27 mois terminés, décision EPFL au mois 28. Les décisions de logement précédentes conservent le logement ; aucun choix EPFL n’est effectué automatiquement. Les finances ne sont pas injectées artificiellement.
- Une démo commencée est sauvegardée comme une partie normale. Pour la rejouer, utiliser Réglages → recommencer (cela remplace la partie locale), saisir le prénom et relancer la démo. Sur Desktop, isoler une session QA avec la propriété JVM `-Dfinlife.saveDirectory=/tmp/finlife-demo` si nécessaire.
- Préparer Ollama avec `ollama pull qwen3:4b`. Le serveur doit être actif avant la présentation. Desktop utilise localhost ; l’émulateur Android utilise 10.0.2.2. Un téléphone réel ne dispose pas de cette connexion par défaut.

## Script chronométré

| Temps | Action et message |
|---|---|
| 0:00–0:30 | « FinLife permet d’explorer des décisions de vie et leurs effets financiers ; il ne prédit pas l’avenir et ne choisit pas à votre place. » |
| 0:30–1:00 | Montrer le prénom, puis ouvrir la démo EPFL. Expliquer le budget fictif et l’historique rejoué. |
| 1:00–2:10 | Lire les deux options : salaire privé par défaut de 6 000 CHF contre 4 500 CHF à l’EPFL, bruts dans le modèle. Les déductions salariales sont de 20 %. Montrer les aperçus calculés. |
| 2:10–2:50 | Expliquer : 10 % de faillite chaque mois éligible, soit environ 71,8 % sur douze mois ; hypothèse fictive forte. Les projections ne tirent aucune nouvelle faillite et ne sont pas des espérances financières. |
| 2:50–3:40 | Accepter EPFL, montrer la comparaison historique puis l’aperçu futur. Distinguer solde bancaire disponible, solde théorique et patrimoine net dans Détails. |
| 3:40–4:20 | Question préparée : « Pourquoi le salaire a-t-il changé au mois 28 ? » Cette question utilise une explication explicitement attribuée au moteur après identification du mois par l’assistant, sans recommander. Ne pas attendre une réponse lente pendant toute la démo : montrer Détails si nécessaire. |
| 4:20–5:00 | Montrer la même décision sur l’autre plateforme, avec le même prénom et les mêmes valeurs. Rappeler que le moteur Kotlin et l’interface sont partagés. |

La ligne pointillée compare des mois déjà joués ; elle n’est pas une prévision. Le parcours privé rejoué
avec la même graine reproduit les mêmes tirages : cela ne signifie pas que les événements réels sont prévisibles.

## Secours sans IA

Si Ollama est indisponible ou si une réponse est rejetée, montrer les faits dans Détails, les aperçus des
options et la note sur les hypothèses. Présenter explicitement cette explication comme celle du moteur,
pas comme une réponse générée. Conserver une capture des deux plateformes avant le passage devant le jury.

## Vérifications distinctes

### Tests

Les tests partagés couvrent les calculs, la probabilité mensuelle, la sauvegarde/reprise du scénario,
les deux choix EPFL, les faillites déjà connues et le rejet de montants associés à la mauvaise grandeur.
Les tests de rendu produisent notamment `shared/build/qa/epfl-demo-390.png` et `epfl-demo-1200.png`.

### Android

La compilation et les tests Android sur hôte ne prouvent pas l’expérience sur appareil. Vérifier sur
émulateur : saisie du prénom, accès à la démo, texte du risque, deux boutons de choix accessibles avec
défilement, choix EPFL, reprise après fermeture et permission réseau de l’assistant. L’assistant d’un
téléphone physique n’est pas configuré ; la simulation reste utilisable hors ligne.

### Desktop

Vérifier : lancement, même parcours, redimensionnement, choix au clavier/souris, sauvegarde/reprise,
et réponse de la question préparée. Les rendus Compose hors écran complètent cette vérification sans
remplacer un lancement natif.

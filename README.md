# FinLife — Technical Specifications

## 1. Overview

FinLife is an interactive financial life simulator for the UBS × LauzHack and JetBrains challenges. Follow a fictional student in Lausanne, named by the player, and explore how housing, work, and unexpected costs affect their cash, unpaid bills, and net worth.

## 2. Core Features

- **Editable budget** — Set starting cash, income, and expenses. The Details view explains monthly results and lets you revise inputs while retaining recorded decisions.
- **What If** — Compare a paid internship, shared housing, or both, or even stop working for 6 months against the current path in a 12-month outlook.
- **Career choices** — After graduation, an EPFL job offer (lower, stable salary) or keeping the private job, which carries a 10% chance each month that the company closes.
- **Financial history** — Advance month by month, track cash and net worth, and review income, expenses, and unpaid bills. A dotted line shows the run with the other side of the latest choice, with a breakdown of why the two lines differ.
- **Transparency** — An Assumptions screen lists every value the engine uses; a goal bar tracks progress toward the engine's net-worth goal.
- **Sensitivity** — Sliders change the rent and the income from parents (−50% to +50%) and rerun the 12-month outlook.
- **AI assistant** — An optional local Ollama model explains simulation results. The Kotlin engine performs the calculations; the assistant gives no advice and changes nothing.

## 3. Technical Specs

| Component | Specification |
|---|---|
| Platforms | Android 7.0+ (API 24) and JVM desktop |
| UI | Kotlin Multiplatform 2.4.21, Compose Multiplatform 1.12.2 |
| Build | JDK 21, Gradle 9.9.1 wrapper, Android SDK Platform 37 for Android builds |
| Model | Shared monthly V3 engine, CHF in integer cents, 2% annual inflation on applicable costs, fixed nominal income, flat synthetic 20% tax on salary |
| Storage and UI | Local save, English and French, light and dark themes |

## 4. How to Run

From the repository root, enter `FinLife`.

| Task | Windows PowerShell | macOS / Linux |
|---|---|---|
| Launch Desktop | `.\gradlew.bat :desktopApp:run` | `./gradlew :desktopApp:run` |
| Install Android debug build | `.\gradlew.bat :androidApp:installDebug` | `./gradlew :androidApp:installDebug` |
| Run shared tests | `.\gradlew.bat :shared:jvmTest :shared:testAndroidHostTest` | `./gradlew :shared:jvmTest :shared:testAndroidHostTest` |

For Android, open `FinLife` in Android Studio, sync Gradle, select an emulator or USB-connected device, and run `androidApp`. If Gradle cannot find the SDK, set `sdk.dir` in `FinLife/local.properties`.

For AI explanations, start Ollama and run `ollama pull qwen3:4b` on the host computer. Desktop connects through localhost. On Android the assistant works in the emulator only: it reaches the host at `10.0.2.2:11434` and needs local-network access when prompted. A physical phone cannot reach it.

## 5. User Flow & Edge Cases

Choose a name → edit their budget → preview a choice → advance a month to apply it → review the results. The simulation works without Ollama. Unpaid bills are tracked separately from available cash. All figures are illustrative. Simulation under stated assumptions, not a forecast or financial advice.

[Developer guide and model assumptions](FinLife/README.md)

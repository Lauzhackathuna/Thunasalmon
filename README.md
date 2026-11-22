# FinLife — Technical Specifications

## 1. Overview

FinLife is an interactive financial life simulator for the UBS × LauzHack and JetBrains challenges. Choose a first name for a fictional student in Lausanne, then explore how housing, work, and unexpected costs affect cash, unpaid bills, and net worth. FinLife is an exploratory simulator, not a financial adviser.

## 2. Core Features

- **Editable budget** — Set starting cash, income, and expenses. The Details view explains monthly results and lets you revise inputs while retaining recorded decisions.
- **What If** — Compare a paid internship, shared housing, or both, or even stop working for 6 months against the current path in a 12-month outlook.
- **Financial history** — Advance month by month, track cash and net worth, and review income, expenses, and unpaid bills.
- **AI assistant** — An optional local Ollama model explains simulation results. The Kotlin engine performs the calculations.

## 3. Technical Specs

| Component | Specification |
|---|---|
| Platforms | Android 7.0+ (API 24) and JVM desktop |
| UI | Kotlin Multiplatform 2.4.21, Compose Multiplatform 1.12.2 |
| Build | JDK 21, Gradle 9.9.1 wrapper, Android SDK Platform 37 for Android builds |
| Model | Shared monthly V3 engine, CHF in integer cents, 2% annual inflation on applicable costs, fixed nominal income |
| Storage and UI | Local save, English and French, light and dark themes |

## 4. How to Run

From the repository root, enter `FinLife`.

| Task | Windows PowerShell | macOS / Linux |
|---|---|---|
| Launch Desktop | `.\gradlew.bat :desktopApp:run` | `./gradlew :desktopApp:run` |
| Install Android debug build | `.\gradlew.bat :androidApp:installDebug` | `./gradlew :androidApp:installDebug` |
| Run shared tests | `.\gradlew.bat :shared:jvmTest :shared:testAndroidHostTest` | `./gradlew :shared:jvmTest :shared:testAndroidHostTest` |

For Android, open `FinLife` in Android Studio, sync Gradle, select an emulator or USB-connected device, and run `androidApp`. If Gradle cannot find the SDK, set `sdk.dir` in `FinLife/local.properties`.

For AI explanations, start Ollama and run `ollama pull qwen3:4b` on the host computer. Desktop connects through localhost. The Android emulator uses `10.0.2.2:11434` and needs local-network access when prompted.

## 5. User Flow & Edge Cases

Choose a name → edit his/her budget → preview a choice → advance a month to apply it → review the results. The simulation works without Ollama. Unpaid bills are tracked separately from available cash. All figures are illustrative. Simulation under stated assumptions, not a forecast or financial advice.

[Developer guide and model assumptions](FinLife/README.md)

## 6. Five-minute demo

After entering a first name, select **EPFL demo · jump to decision** on the budget screen.
This uses the default budget and fixed seed 19, replays 27 completed months with default housing choices,
and stops before the EPFL decision (month 28). It does not use edited setup amounts.
See the [five-minute script and platform checklist](FinLife/docs/demo.md).

The private job has a fictional independent **10% failure probability each eligible month** after
staying in that job: `1 - 0.9^12 ≈ 71.8%` over twelve eligible months. EPFL has no company-failure risk
in this model. Projections include known events but **do not draw new future failures**; they are
conditional scenarios, not forecasts or probability-weighted expected outcomes.

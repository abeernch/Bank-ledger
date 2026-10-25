# Ledger

Personal Android app that logs every bank debit and credit automatically, then helps you budget.
It reads bank SMS and bank app notifications, pulls out the amount, payee, place, time and balance,
and files each one under a category and account.
Everything stays on the phone. The app has no internet permission.

## Install

1. Open the latest release on this repo from the phone and download the APK.
2. Install it. Android will ask to allow installs from your browser or file manager.
3. Open Ledger. On Home, tap **Allow SMS access**, **Allow notification access** and **Allow budget alerts**.
4. If Android says a setting is restricted: Settings → Apps → Ledger → ⋮ → **Allow restricted settings**, then try again.
5. On first SMS access the app imports your past bank messages.

Every build installs over the old one and keeps your data, because all builds share the same signing key (`app/ledger.keystore`).

## What it does

| Tab | Contents |
|---|---|
| Home | Left to spend this month, safe amount per day, money in/out, things that need a look (unread messages, overspent budgets, bills due soon, money owed to you), accounts, recent activity |
| Activity | Every transaction by day. Search across all months, filter by in/out, category, fund, account. Add cash or missed payments by hand |
| Budgets | Monthly limit per category with optional rollover. **Funds** track spending for a purpose (a wedding). **Goals** track savings towards a target |
| Accounts | Balance per bank, wallet, cash or savings account. Set the current balance once and it stays up to date. Merge duplicates. People & debts |
| Insights | 6-month in/out chart, category change vs last month, top payees, spending by weekday, recurring payments |

### Savings locks

Lock part of an account's balance so the app treats it as not usable. The money stays in the bank.

- Accounts tab → **Lock money** on any account. Several named locks per account (e.g. *Plot savings*, *Emergency*).
- **Available to use** = balance minus all locks. Shown per account, in the Accounts total, and on Home.
- A lock can count towards a **Goal**; the goal's saved amount then includes it.
- **Auto-lock**: lock a percent or fixed amount of each incoming credit, optionally only credits above a threshold
  (your salary), optionally including transfers from your own accounts. Not applied during SMS history import.
- Releasing or removing a lock needs your **fingerprint/face or the Ledger PIN**. Forgot the PIN? Reset it with the phone's screen lock.
- If spending pushes an account below its locked amount, you get one warning until it is back above.
- Every add, release and auto-lock is kept in the lock's history.

Tap any transaction to change its category (saved as a rule for next time), assign it to a fund, split it across categories,
add a note or receipt photo, mark it as a move between your own accounts, or record it as a loan.

Settings has the theme (follows the phone, or light/dark), app lock, alerts, your name for own-transfer detection,
category rules, PDF monthly statement, CSV export, backup and restore.

## How it works

| Part | File |
|---|---|
| Incoming SMS | `ingest/SmsReceiver.kt` |
| Bank app notifications | `ingest/BankNotificationListener.kt` |
| One entry point: duplicates, account, category, transfers, alerts | `ingest/Ingest.kt` |
| Known bank formats | `parse/Templates.kt` |
| Fallback for unknown formats (also foreign currency) | `parse/GenericParser.kt` |
| What counts as spending and income | `logic/Money.kt` |
| Budgets, balances, transfers, recurring, insights | `logic/` |
| Database and migrations (v1 → v2 → v3) | `data/` |
| Savings locks | `logic/Locks.kt`, `alerts/SavingsAlerts.kt`, `ui/LockSheets.kt`, `ui/Guard.kt` |
| Screens | `ui/` (one file per tab) |
| Theme and colours | `ui/Theme.kt` |
| Alerts and daily reminder job | `alerts/` |
| CSV, PDF, backup | `export/` |

A message is handled in this order:

1. OTPs, promotions and texts from personal numbers are ignored.
2. A bank template is tried. These give the most complete result.
3. If none matches, the generic parser looks for an amount and a debit or credit word.
4. If money is mentioned but nothing could be read, the message goes to **Messages to review**.

Spending rules, used everywhere (`logic/Money.kt`):

- A move between your own accounts is not spending or income. It is detected when the same amount leaves one account
  and arrives in another within 30 minutes, or when the payee is your own name (set in Settings).
- A split transaction counts through its parts, not its total.
- Totals and budgets are in PKR. Foreign currency transactions are shown but not added to PKR totals.

The same transaction often arrives twice, once by SMS and once from the bank app.
If two alerts have the same amount and direction within 3 minutes, only the first is kept.

## Adding a bank format

1. Copy the message from **Messages to review**.
2. Add it as a test in `app/src/test/.../ParserTest.kt`.
3. Add a `Template` in `parse/Templates.kt` until the test passes.
4. Push. GitHub Actions runs the tests and publishes a new APK.

## Supported formats

| Bank | Format |
|---|---|
| Askari Bank | POS / card purchase |
| Meezan Bank | Funds sent (IBFT) |
| Easypaisa | Virtual debit card payment |
| Any other | Generic parser |

Font: Inter (SIL Open Font License, see `LICENSE-Inter-font.txt`).

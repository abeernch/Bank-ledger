# Ledger

Personal Android app that logs every bank debit and credit automatically.
It reads bank SMS and bank app notifications, pulls out the amount, merchant or payee,
place, time and balance, and files each one under a category.
Everything stays on the phone. The app has no internet permission.

## Install

1. Open the latest release on this repo from the phone and download the APK.
2. Install it. Android will ask to allow installs from your browser or file manager.
3. Open Ledger and tap **Allow SMS access**, then **Allow notification access**.
4. If Android says the setting is restricted: Settings → Apps → Ledger → ⋮ → **Allow restricted settings**, then try again.
5. On first SMS access the app imports your past bank messages.

Every new build installs over the old one and keeps your data, because all builds share the same signing key (`app/ledger.keystore`).

## How it works

| Part | File |
|---|---|
| Incoming SMS | `ingest/SmsReceiver.kt` |
| Bank app notifications | `ingest/BankNotificationListener.kt` |
| One entry point, duplicate check, saving | `ingest/Ingest.kt` |
| Known bank formats | `parse/Templates.kt` |
| Fallback for unknown formats | `parse/GenericParser.kt` |
| Categories | `category/Categorizer.kt` |
| Screens | `ui/` |
| Home-screen widget | `widget/SpendWidget.kt` |

A message is handled in this order:

1. OTPs, promotions and texts from personal numbers are ignored.
2. A bank template is tried. These give the most complete result.
3. If none matches, the generic parser looks for an amount and a debit or credit word.
4. If money is mentioned but nothing could be read, the message goes to the **Review** tab.

The same transaction often arrives twice, once by SMS and once from the bank app.
If two alerts have the same amount and direction within 3 minutes, only the first is kept.

## Adding a bank format

1. Copy the message from the Review tab.
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

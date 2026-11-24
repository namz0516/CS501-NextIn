# NextIn — Orca Sightseeing QR check-in demo

This patch adds an explicitly simulated business named **Orca Sightseeing** (ID `orca-sightseeing`) to the local Room-backed queue, and an Android deep link `nextin://join/orca-sightseeing` that opens its customer join form. It **does not** automatically join without user confirmation. The existing `QueueViewModel.join` flow and phone-to-watch Data Layer continue to work unchanged.

## Apply to the latest project

Extract the ZIP and copy its contents into the project root. Do not copy the full older project over your local files. Build with `./gradlew :app:assembleDebug :wear:assembleDebug`.

## Test without a camera

With the NextIn phone app installed and emulator running (substitute its ADB serial):

```bash
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 shell am start -a android.intent.action.VIEW -d 'nextin://join/orca-sightseeing' com.example.nextin
```

The customer join form should open for Orca Sightseeing. Enter name and party size, join, then check the watch. The accompanying PNG is a scannable QR containing the same URI. You can display it on another screen and scan with an Android device that has NextIn installed. Some generic camera apps may not recognize custom-scheme QR links; the ADB test is the most reliable emulator test.

## Limitations

- This is a **local demo**: it does not add guests to a real business queue or synchronize queues between different phones.
- The guest must install NextIn. App-free check-in requires a hosted HTTPS web page and shared backend.
- QR links identify the business but do not bypass the join form; this prevents unintended joins.
- Wear OS continues to show only the phone app's `demo-customer` queue.
- No changes to Firebase/authentication or the Wear module.
- This QR code is not associated with or endorsed by a real company named Orca Sightseeing.

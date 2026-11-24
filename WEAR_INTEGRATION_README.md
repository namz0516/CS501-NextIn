# NextIn Wear OS companion (MVP)

## Scope
Read-only companion for the existing `demo-customer` queue. The phone owns Room data; the watch shows the last synchronized queue status. **Not a live restaurant queue or a live ETA.** Position uses FCFS joinedAt ordering and is illustrative if a restaurant uses table-fit seating.

## Install
Copy the supplied files into your existing project. The changes add `:wear` in `settings.gradle.kts`, one wearable dependency in `app/build.gradle.kts`, a publisher class, and three Wear module files. The existing phone ViewModel calls the publisher after loading and changing queue state. Do not overwrite your local root `build.gradle.kts` compatibility changes with the archived root file.

## Build
`./gradlew :app:assembleDebug :wear:assembleDebug`

## Run
Create a Wear OS emulator (API 30+), pair it with the phone emulator using Android Studio's Wear OS emulator pairing assistant, and run the `wear` configuration on the watch. Run `app` on the phone. Join a demo queue on the phone; open NextIn on the watch. Both APKs use the same application ID, as required for the Wear Data Layer companion relationship, but install on separate devices.

## Test
1. No queue: watch displays join-on-phone message.
2. Join a demo queue: watch shows restaurant, position, and last-sync time.
3. Staff calls the customer: watch shows Your turn! after sync.
4. Leave queue: watch returns to no-active-queue message.
5. Disconnect devices: watch retains last received state; the last-sync timestamp reveals potential staleness.

## Limitations / follow-up
No watch-side actions, ETA, haptic notification, tile, or complication yet. The phone ViewModel must initialize for publishing; data item persistence provides the last published state when the watch reconnects. No background sync independent of the phone app is implemented. The UI currently shows a sync timestamp but does not explicitly flag old data as stale. `QueueViewModel` already runs expiry checks every second, but only changed snapshots trigger publication. Test the watch module on your Android Studio/Gradle version; this package was not built in the author's environment.

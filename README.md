## NextIn

NextIn is a virtual queue app for customers and business staff, built with Kotlin, Jetpack Compose, and Material 3. Customers can join queues, check their wait times and confirm their arrival, while staff can manage waiting parties and restaurant seating. Queues are shared across devices using Firebase

### Things done so far:
- Welcome screen with customer and staff options
- Customer screens to browse businesses, view details, and join a queue
- Current queue screen showing position, estimated wait time and grace period countdown
- Staff sign-in and dashboard to manage queues, add walk-ins, and call the next party
- Queue management with FCFS and seating rules
- Arrival confirmation and grace period handling
- Shared queues using Firebase Authentication and Firestore
- Queue data cached locally using Room
- Notifications when it's your turn, 60 seconds before the grace period ends, when it ends, and when removed from the queue
- Grace period notifications also arrive when the app is closed
- Nearby places and place search using the Google Places API
- QR code and link to open a business's join form
- Wear OS app showing the customer's queue status
- Unit tests and a Compose UI test

### How it works:
- Jetpack Compose and Navigation Compose handle the screens and user interactions
- QueueViewModel and StateFlow manage the UI state
- Firestore stores the shared queues, and Room keeps a local copy on each phone
- QueueNotifier shows notifications, and AlertScheduler sets alarms for the grace period
- PlacesRepository uses the Google Places API to find nearby places
- PhoneQueueSync sends the queue status to the watch using the Wear OS Data Layer

### Open and run
1. Open NextIn in Android Studio
2. Use Android Studio's embedded JDK 17 or 21 and install Android SDK 35
3. Add your Google Places API key to `local.properties` as `PLACES_API_KEY=your_key`
4. Allow Gradle sync, then run the `app` configuration on an emulator
5. For the watch, pair a Wear OS emulator with the phone and run the `wear` configuration on it

The Places API key is not in the repository because `local.properties` is ignored by Git. Without the key the app still runs, but nearby places won't load

To build the app from Terminal:
./gradlew :app:assembleDebug

To run the unit tests:
./gradlew :app:testDebugUnitTest

To run the UI test with an emulator running:
./gradlew :app:connectedDebugAndroidTest

To open a join form with a link:
adb shell am start -a android.intent.action.VIEW -d "nextin://join/olive-ember"

Staff accounts are created by the team in Firebase

### Not implemented yet
Notifications from the server when the app is fully closed using Firebase Cloud Messaging, sorting nearby places by distance, more UI tests and accessibility improvements


### Project Update 1

The first version was a working local queue system, connecting the Compose screens, ViewModel, repository, and Room database. Customer and staff queue flows were implemented, and local persistence was manually verified

### Project Update 2

Queues now run on Firebase, so customers and staff on different phones see the same queue. Google Places, QR code joining, a Wear OS app and queue notifications were added

### Contributions
- Namratha: initial architecture, navigation, nearby places, QR, Wear OS
- Suchir: Firebase, customer and staff workflows, Room persistence, queue models
- Vijay: notifications, grace period reminders and alarms, watch expiry sync, testing

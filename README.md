## NextIn

NextIn is a virtual queue app for customers and business staff, built with Kotlin, Jetpack Compose, and Material 3. Customers can join queues and check their wait times, while staff can manage waiting parties and restaurant seating

### Things done so far:
- Welcome screen with temporary customer/staff selection
- Customer screens to browse restaurants, view details, and join a queue
- Current queue screen showing position and estimated wait time
- Staff dashboard to manage queues, add walk-ins, and call the next party
- Queue management with FCFS and seating rules
- Arrival confirmation and grace period handling
- Queue data stored locally using Room, with persistence across app restarts
- Navigation Compose routing and base Material 3 theme
- QueueViewModel with StateFlow to update the UI when queue data changes
- Unit tests for queue operations and validation

### How it works:
- Jetpack Compose handles the screens and user interactions
- QueueViewModel and StateFlow manage the UI state
- LocalQueueRepository handles queue operations and business rules
- RoomQueueStore, QueueDao, and NextInDatabase handle local database storage

The app currently uses a local database (nextin-local.db). Firebase will be added later to support shared queues across devices

### Open and run
1. Unzip this folder and open NextIn in Android Studio
2. Use Android Studio's embedded JDK 17 or 21 and install Android SDK 35
3. Allow Gradle sync, then run the `app` configuration on an emulator

To build the app from Terminal:
./gradlew :app:assembleDebug

To run the unit tests:
./gradlew :app:testDebugUnitTest

Both the build and unit tests passed


### Not implemented yet
Firebase Authentication and Firestore for shared queues, Google Places API for nearby restaurant recommendations, location integration, push notifications using Firebase Cloud Messaging, Wear OS (Watch) integration, additional UI tests and accessibility improvements


### Project Update 1

The current version is a working local queue system, connecting the Compose screens, ViewModel, repository, and Room database. Customer and staff queue flows were implemented, and local persistence was manually verified

The next step is to connect Firebase and the remaining external services for Project Update 2

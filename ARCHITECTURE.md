# NextIn — Initial architecture

This first PR creates the Compose UI foundation and a single root NavHost with separate customer and staff route definitions. The role selection is temporary; authenticated role routing comes later.

Planned layers (not yet implemented):
- UI: Jetpack Compose screens, Material 3
- Presentation: ViewModels with StateFlow
- Data: repositories for Firestore queues and Google Places recommendations
- Local persistence: Room for cached businesses/active queue, DataStore for preferences
- Firebase Auth: anonymous customers, email/password staff
- Future integrations: notifications and Wear OS Data Layer

No real queues, Firebase calls, authentication, or Wear OS behavior are included in this scaffold.

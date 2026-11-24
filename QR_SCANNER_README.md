# NextIn QR camera scanner demo

This patch overlays the earlier Orca QR deep-link integration. Keep your existing app and Wear OS module.

The welcome screen now has a **Scan QR** button at the top. Google Code Scanner opens a camera-based scanner, handles camera access in its own Google Play services UI (no CAMERA permission requested by NextIn), and accepts only the demo `nextin://join/orca-sightseeing` QR. It opens the join form, where the customer enters a name and party size and taps Join Queue. The existing My Queue and Wear OS synchronization continue to work.

The app intentionally does **not** join before customer confirmation; the current queue model needs name and party size. No Firebase or anonymous web check-in yet. This is a local demo queue, not a real business queue.

Existing Room snapshots are augmented with the Orca demo business on startup, preserving existing queue entries.

Test: install rebuilt phone APK, tap Scan QR on welcome screen, scan the included PNG displayed on another screen, complete join form, check My Queue and watch. The phone emulator camera may require its virtual scene/webcam configuration; a physical Android device with Google Play services is easiest for a true camera test. Scanner may download its module on first use and requires Google Play services.

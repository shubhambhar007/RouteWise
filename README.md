# RouteWise Android

Native Kotlin / Jetpack Compose implementation, started November 9, 2026.
Version 0.5 is a native, local-first personal-use development build. Following the owner's zero-budget requirement, maps/search/routing use open-data community services instead of paid Google APIs. Optional Groq AI requires the user's own provider key; optional Google in-app navigation requires a restricted Navigation SDK key and a billing-enabled Cloud project. Both are off until configured/selected. This is not a public-release or unlimited-service guarantee.

## Available Now

- Create, rename, duplicate and delete trips.
- Add days; deleting a day preserves its stops in Unassigned.
- Add/edit user-entered destinations or coordinates, visit duration, notes and priority.
- Drag stops into order, use accessible move-up/down actions, or move to another day.
- Explicit visited/skipped progress and next-stop Google Maps handoff, with browser fallback.
- Optional in-app Google Navigation SDK for the remaining located stops on a day, with foreground-only precise-location permission, per-start data/billing consent, explicit End navigation cleanup, and a separate Google Maps fallback. It calculates a fresh Google route and can differ from the OSM preview.
- Room persistence and independent JSON trip copies through the Android Sharesheet and file picker.
- Receive Google Maps text shares, confirm supported explicit destination/Place ID links, and manually resolve unsupported short links.
- No product stop-count limit; 1,000-stop import/export and database operations are covered by tests.
- An optional, explicitly named Jaipur sample trip. It is not a validated itinerary or provider route.
- One-tap "Try Jaipur 3-stop simulation" creates a fresh, separate trip with verified OSM references for Hawa Mahal, City Palace, Jantar Mantar and a Jaipur Junction round-trip anchor. It does not modify existing trips or use an unverified origin. Enable online maps and explicitly calculate its route to simulate arrival/break timing.
- Native MapLibre map with numbered stops, start/finish markers, map-pin capture, fit-to-trip and visible OpenStreetMap attribution.
- Explicit Photon place search with confirmed coordinates; no background geocoding or Nominatim autocomplete.
- User-triggered OSRM driving distance/time and road geometry, batched and rate-limited; incomplete results never show a complete total, and changed inputs invalidate old previews.
- Dated/named days, start and finish anchors, and navigation to the day's finish after visits.
- Durable pending-share inbox: cancelled imports stay available across process death until saved or discarded.
- Deterministic day suggestions: Quick/Balanced/Relaxed visit-duration ranges, arrival/departure estimates from current road legs, meal/rest prompts after long driving, and a visible overlong-day warning.
- Virtual trip playback: play, pause, reset, scrub the day, and choose 1x/5x/15x speed. It advances across drive, visit and break boundaries using the planned route; no GPS, live tracking or visit-status changes are used.
- The sparkle review works without a route for offline visit-time suggestions; arrival estimates and break prompts become available once all day locations are confirmed and a complete route is calculated.
- Review-first changes: select suggested durations and explicitly Apply atomically. Nearby break search is a separate user action, distance-limited to 5 km from an approximate route point. A venue is inserted only after confirmation as an optional stop with source attribution and a suggested-time note.
- Optional Groq AI break planner: after finding real Photon/OpenStreetMap candidates, ask AI to rank only those candidates using break type/time, pace and an optional preference. Candidate IDs are checked against the map results. Rankings require per-request consent, are unverified planning hints, and never modify the trip. The key is encrypted with Android Keystore and can be removed in settings. The app limits this feature to 12 requests per day; that is not a billing cap.
- Plan revisions invalidate the review; duration suggestions reject stale plans/progress and preserve notes/status. Adding a real break stop marks the route stale for explicit recalculation.

No map API key, account sign-in, billing account, paid backend, location permission, or AI is required for core planning. Groq AI is separately opt-in and requires the user's own API key and internet access; check the user's Groq plan/terms for quotas and pricing before enabling. Google in-app navigation is separately opt-in, requires a restricted Google Navigation SDK key and billing-enabled Cloud project, and uses precise foreground location only after the user starts it. Google receives the current device location and confirmed day destination coordinates to compute a fresh route; Navigation SDK charges may apply after the monthly free allowance. Internet access for maps is used only after enabling online maps. Search text and optional nearby-search coordinates go to Photon, route coordinates go to FOSSGIS, and viewed map areas go to OpenStreetMap. With per-request consent, Groq receives break type/time/duration, pace, the user's optional preference, and candidate OSM IDs/names/addresses/categories/distances; it does not receive trip/day IDs, planned stop names, exact route coordinates, notes or visit progress. No background tracking, automatic route refresh or offline bulk tile downloads. Opening navigation never marks a stop visited.

## Free Provider Limits

- Enable the online option using the settings icon. It is off by default and has a provider/privacy disclosure.
- Photon public service allows reasonable use, with no availability guarantee: [policy](https://github.com/komoot/photon#demo-server).
- FOSSGIS routing allows at most one request per second and no heavy use: [policy](https://routing.openstreetmap.de/about.html). The app serializes search/routes with at least 1.1 seconds between starts, uses no automatic retries, and limits each route preview to 100 located nodes (including anchors). Saved stop storage remains uncapped.
- OSM tiles use an identifying User-Agent, HTTP caching, visible attribution, and no prefetch/download feature: [policy](https://operations.osmfoundation.org/policies/tiles/).
- Limits are not a free production SLA. This configuration is for low-volume personal testing; public distribution needs provider permission/capacity review. Provider URLs can be changed in settings without an APK replacement.
- Estimates are driving-only, without live traffic, opening-hours checks, parking, visit time or guaranteed arrival. The Roads API is OSRM, not Google Routes. External Google Maps may choose a different road route.
- Duration ranges use explicit OpenStreetMap categories where present; name-based fallbacks are labelled guesses. Meal/rest prompts are planning heuristics, not venue claims.
- Break search uses a 5 km straight-line filter around an approximate point interpolated on road geometry, not an actual route detour. A map result does not verify operating hours, access, safety, facilities, price or suitability. Recalculate driving route after adding a venue.

## Not Implemented Yet

Automatic itinerary/day assignment or reordering, opening-hours feasibility, traffic prediction, verified journey feasibility, cloud sync, live replanning, automatic expansion of every Google short link, and a public-store release. Suggestions are deterministic except for the separately opted-in Groq candidate ranking, and make no claim of traveller consensus, live venue facts, or artificial intelligence beyond that optional model feature. Google in-app navigation is optional and cannot be tested until an owner supplies a restricted key and accepts Google's terms. Search is explicit rather than autocomplete to reduce community-service load.

The app uses three Room tables with embedded destination references. OSM search selections retain source ID and category; user-entered coordinates remain distinct. Unresolved search text/Google Place IDs can be handed to Maps, but require a confirmed location before in-app routing. No Google API response data is persisted. Room migrations preserve records from schema 1 through schema 4; pending shares are stored privately and excluded from Android backup.

The file format is `routewise.local-trip`, version `4`, with explicit version/format and attribution; versions 1-3 are still accepted. Day dates, anchors and OSM categories travel with a copy. Imports validate destinations, references, durations, timezone and file size (8 MiB), then create new IDs and reset progress atomically. User-authorized visit-time suggestions and break stops become ordinary trip edits, not retained provider route calculations. Exported files contain user trip content; recipients can read them. Share-cache copies expire on a subsequent share after 24 hours. Android automatic backups are disabled; export before uninstalling.

## Build

Open this directory in Android Studio. Use the existing approved JDK/Android SDK setup; do not reinstall or change work account credentials.

- JDK 17+ (verified with Temurin 21.0.2).
- Android SDK platform 35 and build tools 35.0.0.
- Checksum-pinned Gradle 8.13 wrapper, AGP 8.9.2, Kotlin 2.1.20.
- Set `ANDROID_HOME` or the untracked `local.properties` SDK path.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew -PisolatedTests :app:connectedDebugAndroidTest
./gradlew :app:installDebug
```

Connected tests require `-PisolatedTests` and run as `app.routewise.qa`; a Gradle guard rejects unisolated runs. This prevents instrumentation cleanup from uninstalling the main `app.routewise` app. Rebuild without this flag before handing over the APK, since the APK output path is shared. Use an awake, unlocked, authorized emulator and the managed bundle's existing approved ADB key through `ADB_VENDOR_KEYS`.

The live test is skipped by default. To opt in to a small real search/route/map journey, add `-Pandroid.testInstrumentationRunnerArguments.class=app.routewise.ui.TripFlowTest#liveSearchRouteAndNativeMap -Pandroid.testInstrumentationRunnerArguments.runLiveMaps=true`. It is dependent on network and public-service availability, not a hermetic release gate. It writes a screenshot to `/sdcard/Download/routewise-live-map.png` when it succeeds.

On the managed emulator, online access requires its approved network proxy. Temporary proxy changes during testing must be restored. Debug-only Android network security configuration trusts user-installed CA certificates already on the device; it does not install certificates, bypass TLS, or change release trust anchors.

Use the approved per-command proxy settings for dependency downloads where required. On the verified macOS environment, Java downloads succeeded using the existing macOS trust store via `JAVA_TOOL_OPTIONS='-Djavax.net.ssl.trustStoreType=KeychainStore -Djavax.net.ssl.trustStore=NONE'`. This does not disable TLS validation or import new certificates. Never disable certificate validation to make a build work.

APK: `app/build/outputs/apk/debug/app-debug.apk`. This is a debug-signed development artifact, not a Play-ready release. The target SDK/toolchain, 16 KB page support, release signing, accessibility, localization resources, privacy policy, licensing and billing gates must be reviewed before distribution. UI copy is currently English-only and not yet fully externalized into Android resources.

## Tests and Structure

- `core/TripRules`: ordering, coordinate validation and 27-node batches with exactly-once leg coverage.
- `core/TripTransfer`: bounded, validated JSON copies; no provider payload cache.
- `core/MapsLinks`: documented URL parameters, host restrictions, encoded next-stop navigation.
- `data/`: Room transactions, unique ordering, revision/progress separation, cross-trip validation.
- `ui/`: Hilt ViewModel, lifecycle-aware flows, Navigation Compose, native screens and platform actions.
- JVM tests exercise 0..1,002 route-node boundaries, 1,000-stop transfer, invalid data and URL handling.
- Emulator tests exercise 1,000-stop persistence operations, cross-trip rejection, editing/progress/day moves, drag ordering and recreation.

### Verification and Data Safety

On November 10, v0.3 passed **20 JVM tests and 16 isolated Android 16 emulator tests**; one additional live-network test was skipped. Emulator coverage includes route suggestions, offline review/playback, atomic edits, venue candidates, non-destructive schema migration and the one-tap verified Jaipur trip. `:app:lintDebug` reported zero errors and 45 warnings. A live search/save/route/map run succeeded once through the approved proxy; later attempts timed out. Nearby break search is covered by mock-provider tests, not repeated live emulator requests. Public services are best-effort.

Data-safety incident: unisolated instrumentation cleanup uninstalled the main debug package, and an immediate post-install directory check found no database. This was initially reported as data loss, but a later inspection showed a saved Jaipur sample with 10 stops. The available evidence does not establish whether earlier records were retained, restored or recreated; neither loss nor recovery is confirmed. Source files were unaffected. Tests are now isolated and guarded as described above. Never assume a connected-test task preserves an existing app's data.

The library screen was visually checked at the original 427dp width and at 320dp with 130% text, with no clipping observed; original 1280x2856 size, 1.0 font scale and unset emulator proxy were restored. Compact editor/map review, physical-device navigation, full share-format coverage, minimum-SDK testing and public-release licensing/capacity checks remain open.

## Assets

Manrope is bundled under the SIL Open Font License; the complete notice is in `app/src/main/res/raw/manrope_license.txt`. UI icons are supplied by AndroidX Material Icons. Drag interaction uses Calvin Liang's Apache-2.0 Reorderable library. MapLibre Native is BSD-2-Clause; OpenStreetMap data is attributed to its contributors under ODbL. Provider logos/attribution must not be hidden.

## Product Specification

[PRD](docs/PRD.md), [target architecture](docs/ARCHITECTURE.md), and [delivery and provider constraints](docs/DELIVERY.md) remain the R1/R2 planning baseline. Paid API activation and cloud deployment remain separate owner decisions. This implementation does not authorize spending or public release.

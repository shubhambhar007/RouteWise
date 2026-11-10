# RouteWise: Decisions, Delivery, Costs, and Evidence

Version: 0.1 | Date: 2026-11-09 | Status: proposed plan, not authorization to spend or deploy

Related: [PRD](PRD.md), [Architecture and contracts](ARCHITECTURE.md).

## 1. Decisions to Approve

| ID | Proposed decision | Why it matters | Approval/status |
|---|---|---|---|
| D-01 | Native Android, Kotlin + Compose | Explicitly requested; no web-first substitute | Confirmed by handover and follow-up |
| D-02 | Ship routing MVP before automatic planning | Keeps the first release testable and avoids marketing unbuilt intelligence | Recommended; owner confirmation pending |
| D-03 | Local-only trip storage, no account UI | Removes sync/collaboration work; export needed for backup | Recommended; owner confirmation pending |
| D-04 | Private pilot with 10-20 people | Validate actual travel friction before public launch | Recommended; owner confirmation pending |
| D-05 | Manual days plus start/end anchors | Makes a 2-7-day leisure trip usable before an optimizer exists | Recommended; owner confirmation pending |
| D-06 | Single-next-stop Maps navigation is the reliable default | Consumer Maps waypoint support varies; no arrival callback | Recommended; test on physical devices |
| D-07 | Small authenticated Routes broker in MVP | Protects paid server credentials and limits abuse | Recommended; cloud ownership/budget approval pending |
| D-08 | No permanent cache of Google names, route times, polylines, or opening hours | Google's current storage terms are narrower than a typical offline trip database | Implementation/licensing gate |
| D-09 | Independent user labels and Place IDs form the durable Google-backed trip record | Safe default may reduce offline convenience and increase refresh costs | Product and licensing approval pending |
| D-10 | No payments, LLM, or live tracking in R1 | Validate the core job before adding cost, policy, and UX complexity | Recommended |
| D-11 | Use a personal billing account and approved development environment | Separate personal product IP/data/billing from work resources | Owner must verify employer/device policy |
| D-12 | English UI, India driving, proposed minSdk 26 | Reduces initial localization and mode scope | Confirm target device reach before freeze |

No answers were available to the clarifying questions during drafting. Nothing in this document implies approval of a billing account, paid budget, cloud deployment, or public launch.

### Recommended First Build After Approval

Build the real native screens and Room persistence, then search/share capture, then route preview and navigation. The first clickable APK can use clearly labelled synthetic test fixtures, but a fixture-only demo is not the MVP. Do not spend a week building an AI chat screen before validating capture and navigation.

## 2. Important Unknowns and Release Gates

| Gate | Unknown | Evidence required | Blocks |
|---|---|---|---|
| G-01 Licensing | May this implementation persist/export a user-selected plan derived from Google content, and exactly which fields? | Written field-by-field policy review against applicable contract; counsel/Sales review where needed | Permanent provider-data retention, derived plan sharing, public release |
| G-02 Sharing | Which real Google Maps Android share formats are resolvable without scraping? | Consented fixtures across app versions/locales; safe fallback measured on at least 30 inputs | Claiming automatic share import coverage |
| G-03 Handoff | Which destination/chunk URLs work on the target Maps app/browser versions? | Physical-device pass/fail matrix, including absent Maps and denied location | Reliable navigation claim |
| G-04 Billing | Is the owner eligible for India pricing? What are actual SKU triggers for selected SDK fields/session flow? | Correct billing country, usage geography, billing-console SKU smoke tests, budget approval | Enabling paid APIs for testers |
| G-05 Platform | Do the selected Maps/Places SDKs, Compose/AGP/JDK, emulator, and target SDK work together? | Pinned version catalog and successful signed release/debug builds on emulator and phone | Implementation baseline |
| G-06 Data quality | Are entrances, driving access, opening hours, and last admission reliable for pilot destinations? | Human checks on 20 representative attractions; explicit unknown states | R2 scheduling claims |
| G-07 Value | Is this easier than Maps Saved lists plus a competitor? | Five competitor task walkthroughs and 10 user sessions; record failed tasks and willingness to pay | Public positioning and pricing |
| G-08 Operations | Can paid traffic be throttled safely under abuse or retry storms? | Attestation/auth tests, quota tests, separate native-SDK quota monitoring, kill-switch drill | External pilot |
| G-09 Distribution | Are developer verification and testing requirements met for the chosen Play account? | Current Play Console requirements, privacy/Data Safety details, signing ownership | Play release date |
| G-10 Ownership | Is personal product development permitted on the employer-managed laptop and existing tooling? | Owner confirms applicable internal policy, licenses, and IP rules | Commercial implementation/deployment on work resources |

Engineering documentation is not legal advice. Technical support cannot grant contractual exceptions. Do not treat "the user tapped Save" as blanket permission to copy Google Places content into a permanent database.

## 3. Verified Google Platform Facts

Verified by reading the official pages on 2026-11-09. Prices/terms/SDK behavior can change; revalidate before implementation and release. Source IDs refer to section 10.

| Topic | Verified fact | Product implication | Sources |
|---|---|---|---|
| Compute Routes | Maximum 25 intermediate waypoints, excluding origin/destination | 27-node request batches with overlapping boundary nodes | S1 |
| Route billing | 11-25 intermediates or waypoint optimization or traffic-aware routing triggers Pro | A 50-visit overview is not necessarily Essentials-priced | S1, S4 |
| Matrix | Normally <=625 elements; <=100 for transit or traffic-aware-optimal; address/Place ID origins+destinations <=50 | Tile by both element count and waypoint representation; bill elements, not HTTP calls | S1, S3 |
| Default quotas | Routes 3,000 queries/min; matrix 3,000 elements/min | Two dense 52-point plans can exceed default matrix throughput | S1 |
| Maps URLs | API key not required; max URL length 2,048 characters | Construct encoded URLs and count final length | S2 |
| URL waypoints | Up to three on mobile browsers and up to nine otherwise; not supported by every Maps product | Never assume one URL is unlimited or always honors waypoints | S2 |
| Navigation | `dir_action=navigate` can open preview when origin is unavailable/not near traveller | Default next-stop handoff omits origin; test actual device behavior | S2 |
| Native map pricing | Maps SDK is listed with unlimited free usage, unlike Dynamic Maps JavaScript pricing | Do not incorrectly charge web map-load prices to native Android map display | S5, S6 |
| India pricing | Requires billing in India and a large majority of usage in India | Indian travellers alone do not prove eligibility | S7 |
| Identifiers | Place IDs can be stored indefinitely under the documented exception | Durable trip references can survive process death | S8, S10 |
| Coordinates | Relevant services permit temporary latitude/longitude caching up to 30 days | Not a blanket 30-day cache license for names, hours, directions, or matrices | S8, S9, S10 |
| Content | Scraping, unauthorized caching/export, and certain derived-content uses are restricted | Keep provider payloads out of Room/export/logs; review optimizer retention separately | S8, S9, S10 |
| Attribution | Visible Google Maps and applicable third-party attribution is required | Preserve SDK logo and add proper attribution to non-map provider-content screens | S8, S9 |
| Secrets | Server web-service credentials are not intended for public APK exposure | Native keys restricted by package/signature; backend secrets separate | S11 |

Google Navigation SDK is a different product from opening the consumer Maps app. It has separate destination-based billing and limits; it is not included in the MVP. Do not apply Navigation SDK prices to Maps URL launches.

The provider's India FAQ includes some older tables; use the current India pricing list (S6) and billing-console SKU evidence as the operational reference, not a copied historic FAQ number.

## 4. Cost Model

### Pay-As-You-Go Rates Relevant to This Plan

All rates are USD per 1,000 billable events at the first paid tier. Free caps are per SKU per month, aggregated as described by Google across applicable billing-account projects, not per user or trip. Higher-volume tiers differ. Tax, foreign exchange, hosting, support, and payment fees are additional.

| SKU | India free cap | India first paid rate | Global free cap | Global first paid rate | Unit |
|---|---:|---:|---:|---:|---|
| Maps SDK | Unlimited | 0 | Unlimited | 0 | Native map usage under this SKU |
| Autocomplete Requests | 70,000 | 0.85 | 10,000 | 2.83 | Request |
| Place Details Essentials | 70,000 | 1.50 | 10,000 | 5.00 | Request |
| Place Details Pro | 35,000 | 5.10 | 5,000 | 17.00 | Request |
| Place Details Enterprise | 7,000 | 6.00 | 1,000 | 20.00 | Request |
| Place Details Enterprise + Atmosphere | 7,000 | 7.50 | 1,000 | 25.00 | Request |
| Compute Routes Essentials | 70,000 | 1.50 | 10,000 | 5.00 | Successful request |
| Compute Routes Pro | 35,000 | 3.00 | 5,000 | 10.00 | Successful request |
| Compute Route Matrix Essentials | 70,000 | 1.50 | 10,000 | 5.00 | Element |
| Compute Route Matrix Pro | 35,000 | 3.00 | 5,000 | 10.00 | Element |

Source: S5 and S6. Only the highest applicable SKU is charged for a given request under these feature rules; do not sum Essentials plus Pro for the same detail/route request.

Field mapping matters (S14): `id` and `name` can be IDs-only, but `name` is the resource string `places/PLACE_ID`, not the human-readable attraction name. `location` is Essentials; `displayName` and `businessStatus` are Pro; `currentOpeningHours` and `regularOpeningHours` are Enterprise. These standalone field tiers must not be confused with the terminating-session rules below. A comprehensive business-status check at initial selection would change the proposed minimal field profile and its cost.

### Autocomplete Billing Is Not Universally Free

Per the current Android session-pricing documentation (S12):

- An Essentials-terminated session bills its first 12 autocomplete requests plus the terminating Essentials details request; later autocomplete requests in that same valid session use the no-charge Session Usage SKU.
- An ID-only terminating request does not earn this treatment: autocomplete requests revert to per-request billing.
- A session ending with non-Essentials Place Details fields has no-charge autocomplete requests, but its terminating request is billed at Enterprise + Atmosphere according to the current session rules, even if a standalone request for those fields would have been Pro.
- Abandoned/invalid/reused-token sessions revert to per-request autocomplete charging.
- Same-session requests must use valid compatible project/API configuration; terminate once and rotate the token.

Proposed MVP search profile: autocomplete -> confirm selected suggestion -> fetch ID/location with an Essentials field set using the same token. Keep the current prediction label only for active display; do not persist it. A later fresh display-name lookup is a standalone appropriately masked details request, not reuse of an expired autocomplete token.

This profile needs a billing-console spike against the exact Places SDK version before being treated as an implemented optimization. An alternative non-Essentials terminating flow must use its actual session SKU in forecasts. Do not request extra fields merely to make autocomplete look free.

### Worked MVP Unit Economics

Illustrative workload, not measured usage: one 50-visit day, explicit start/end, five autocomplete requests per selected place, one Essentials termination per selection, two later complete fresh-name/location resolutions, and three route calculations.

| Event | Count | India gross cost | Global gross cost |
|---|---:|---:|---:|
| Autocomplete requests | 250 | 0.2125 | 0.7075 |
| Selection Details Essentials | 50 | 0.0750 | 0.2500 |
| Later standalone Details Pro | 100 | 0.5100 | 1.7000 |
| Route Pro: two batches x three previews | 6 | 0.0180 | 0.0600 |
| Total before free monthly allowances | | **0.8155** | **2.7175** |

This excludes searches abandoned without a selection, short-link fallback searches, failed/duplicate upstream dispatches, additional reopens, photos, and all R2 features. In this example Places hydration dominates route-preview cost. No permanent name cache is assumed to make costs appear lower.

For 20 testers making three such trips each, monthly counts are 15,000 autocomplete, 3,000 Essentials details, 6,000 Pro details, and 360 route-Pro requests. With no other usage in those SKUs, eligible India Maps charges would be zero under the listed caps; global charges would be approximately USD 31.15 (USD 14.15 autocomplete + USD 17 Pro details). Hosting/taxes remain separate.

For 1,000 such trips/month, eligible India first-tier charges after caps would be approximately USD 484.50: USD 153 autocomplete plus USD 331.50 Pro details; the other two counts remain below their India caps. This scenario is not a forecast and changes materially with real search/reopen behavior.

### Optimization Is a Different Cost Profile

- 52 distinct planning points -> 2,704 elements for one dense matrix: gross USD 4.056 at India Essentials rates, or USD 8.112 at India Pro rates, before monthly caps.
- The corresponding global gross matrix costs are USD 13.52 and USD 27.04.
- Three departure-time matrices roughly triple element charges. Tiling into more requests does not reduce elements.
- Opening-hours requests and candidate meal searches are additional. Use standalone current field-mask SKU pricing, not an assumed free lookup.
- Before committing to dense matrices in R2, compare OR-Tools plus matrix costs with Google's managed Route Optimization API and other appropriately licensed routing providers. Current India managed optimization list rates are USD 0.80/1,000 shipments for Single Vehicle and USD 2.40/1,000 shipments for Fleet, subject to feature and billing definitions (S4,S6). Multiple modeled day-vehicles can change the SKU. These are not feature-equivalent quotes; test model expressiveness, outputs, constraints, and contract suitability first.
- The OR-Tools design is technically feasible, but its road-data input cost may make INR 99-199 per-trip pricing unworkable for large plans. This is a product gate, not a reason to hide costs.

### Spending Controls

- No billing activation or paid smoke test without an owner-approved project and budget.
- Suggested pilot planning envelope: INR 1,000-2,000/month total, to be approved after choosing eligibility and hosting. This is not a spending authorization or guaranteed bound.
- Alert at 25%, 50%, 80%, and 100% of the approved budget; explicitly state that Cloud Billing alerts do not halt spending.
- Use API restrictions, provider project quotas, backend dispatch/element reservations, per-UID and IP limits, pilot admission, and an emergency kill switch.
- Backend counters cover broker-routed requests only. Direct Places SDK traffic is not reliably counted/enforced by a server receiving only client reports. Use supported App Check and Google project/method quotas plus monitoring; do not claim a precise global currency cap from those alone.
- If a strict all-API spending ceiling is a hard requirement, revisit whether paid Places calls should move behind an authenticated service or whether a provider-supported enforcement mechanism is sufficient. Validate its UX, session-token and API-security implications before changing the native-SDK design.
- Do not auto-recalculate on every drag, screen recomposition, app resume, or network reconnection. Make chargeable refresh explicit and bounded.
- Reserve provider-event counts before dispatch; an unacknowledged network response does not imply the provider did not charge.

## 5. Epics, Stories, and Acceptance Criteria

All R1 stories below are required unless explicitly labelled later. Each story includes a testable exit condition; a mocked UI alone does not satisfy live-provider integration stories.

### E-00: Product and Provider Spikes

**US-00.1: Confirm a buildable Android baseline.** As a developer, I can build/install a release-like APK on the approved emulator and a physical phone.

- Record min/target/compile SDK, AGP, Gradle, Kotlin, Compose, JDK, Maps/Places SDK versions, and emulator path/image.
- Do not overwrite work Git credentials or AI sign-ins. Personal product billing/signing is separately owned.
- Reuse existing SDK/emulator tooling; any needed installation follows the user's internal guidelines.

**US-00.2: Verify provider boundaries.** As a product owner, I know what can be stored, billed, imported, and navigated.

- G-01 through G-05 have evidence records and a decision owner.
- Demonstrate a 27-node route request and a 28-node split, an invalid request, Maps next-stop handoff, and unsupported-share fallback.
- Billing console confirms actual route and Places field/session SKUs; do not infer them only from SDK method names.

### E-01: Trips and Days

**US-01.1: Create/edit local trips.** As a traveller, I can maintain trips without network access.

- Create/rename/duplicate/delete pass unit and Room tests.
- Force-stop after an acknowledged save preserves data; duplicate IDs are regenerated and progress resets.
- Changing a date range moves affected visits to Unassigned only after confirmation and never discards them.

**US-01.2: Manage days and anchors.** As a traveller, I can select where each day starts and ends.

- Undated days work; anchors are explicit before route calculation.
- Cross-trip anchor references are rejected transactionally.
- Deleting a day preserves its visits in Unassigned in their relative order.

### E-02: Place Search and Stop Details

**US-02.1: Add a searched place.** As a traveller, I can confirm the correct attraction.

- Query debounce/session-token lifecycle tested; stale query results cannot overwrite current ones.
- No reviews/photos/hours fetched in the MVP field profile.
- Confirmed reference survives relaunch; provider name/address are not copied into persistent columns.
- Duplicate Place ID presents a choice; choosing another visit generates a distinct visit ID.

**US-02.2: Edit visit preferences.** As a traveller, I can add my own label, note, duration, and must-visit flag.

- Valid input persists offline; out-of-range durations and oversized text fail without destroying the last saved value.
- User-authored and provider information are visually/source-model separated.
- A must-visit flag does not pretend to create automatic planning in R1.

### E-03: Google Maps Share Intake

**US-03.1: Capture a place from Sharesheet.** As a traveller, I can save a place while browsing Maps.

- Android cold-start and warm-start intent tests pass.
- Supported long URLs and recognized short links reach confirmation; ambiguous links require search rather than guessing.
- Viewport-only URLs are not treated as exact attraction coordinates.
- Rotation/re-delivery produces one pending/save action, not duplicate visits.

**US-03.2: Handle unsafe/unresolvable imports.** As a traveller, I keep the intended reference when import cannot finish.

- Offline imports remain visible as Pending and are excluded from routing.
- Reject private/loopback/metadata IPs, DNS rebinding, redirect loops, non-HTTPS redirects, userinfo, oversized content, and unsupported schemes.
- No Google page scraping or undocumented token dependency is used to pass the happy-path test.

### E-04: Large Collections and Map

**US-04.1: Arrange many stops.** As a traveller, I can reorder more than 50 visits.

- 51st, 100th, and 500th visits can be saved/reordered; 1,000-stop persistence fixture passes.
- Drag and accessible move controls produce the same stable order, preserving notes, flags, and visit state.
- No route calls occur while dragging; interrupted Room reorder rolls back to a valid sequence.

**US-04.2: Inspect stops on a native map.** As a traveller, list and map agree.

- Marker numbering matches the selected day's list after reorder and day changes.
- Same-coordinate visits remain selectable; start/end markers remain distinct roles.
- Map failure does not block list editing; attribution stays visible at maximum font scale and with the bottom sheet expanded.

### E-05: Route Computation

**US-05.1: Preview an entire selected day's route.** As a traveller, I see all required driving legs.

- Architecture boundary cases 0,1,2,12,27,28,52,53,54,102,502 match expected request and leg counts.
- Randomized ordered-node tests assert exact adjacency coverage without duplication.
- A real 50-visit plus anchor preview uses valid batches and preserves all visit IDs.
- Distance/duration totals equal successful leg sums; estimates explicitly exclude live traffic and visit time.

**US-05.2: Recover from partial results.** As a traveller, I understand incomplete coverage.

- Failure of batch 1 after successful batch 0 shows batch 0 plus the unresolved gap, not a full-trip success.
- A stale result after reorder is discarded; cancel/retry does not create unbounded duplicate dispatches.
- Quota exhausted stops further paid requests but leaves local editing and next-stop handoff available.

### E-06: Navigation and Progress

**US-06.1: Navigate the next destination.** As a traveller, Google Maps receives the intended stop.

- Verify Place ID and coordinate destinations on a physical Android device; no API key in Maps URL.
- Test Maps installed, disabled/absent, browser fallback, denied location, and distant planned origin.
- Persist the target before launch; resuming does not automatically mark visited.

**US-06.2: Resume a long journey.** As a traveller, I can visit/skip stops and continue.

- Only explicit actions alter progress; must-visit skip requires extra confirmation.
- Process death during external navigation restores the same target/progress.
- Optional chunk URLs preserve order, respect character/waypoint bounds, and always offer per-stop fallback; no promise of automatic chunk advancement.

### E-07: Trip Sharing and Backup

**US-07.1: Share a copy.** As a traveller, I can send my trip using Android Sharesheet.

- Preview shows what will leave the device; payload contains only permitted references and user-authored data.
- Round-trip export/import preserves title/day/order/duration/priority/notes but regenerates IDs and resets progress.
- No Google-derived route geometry, estimates, names, photos, reviews, or expired coordinates leak into export fixtures.

**US-07.2: Import defensively.** As a recipient, I cannot accidentally corrupt existing trips.

- Invalid JSON, unsupported versions, missing references, duplicate refs, invalid coordinates, and oversized payloads fail atomically.
- Import creates an independent copy; repeated imports are offered as a new-copy choice rather than silent duplication.
- Files use restricted `content://` grants, expire after 24 h, and are excluded from backup.

### E-08: Secure Broker and Operations

**US-08.1: Protect paid operations.** As the operator, unauthorized clients cannot freely spend against my project.

- Missing/wrong/expired Auth and App Check tokens fail before provider calls.
- Inputs cannot request arbitrary endpoints, fields, traffic modes, or expensive features.
- Secrets absent from APK/repository/logs; Android package and signing-certificate restrictions tested with a negative application identifier.

**US-08.2: Bound cost and retries.** As the operator, I can disable paid routing safely.

- Atomic per-user/global broker counters work across two instances and concurrent requests.
- Same idempotency key/payload coalesces in-flight dispatch; different payload is rejected.
- Completed receipt without retained content yields the specified explicit error, not another silent paid dispatch.
- Kill switch and budget-metadata failure fail closed; direct Places SDK quota limitations are documented and separately tested.

### E-09: Reliability, Privacy, and Accessibility

**US-09.1: Keep user data safe.** As a traveller, my trip is usable offline and deletable.

- Room migrations from all released schemas pass; no destructive migration fallback.
- No background location or broad storage permission; location denial leaves planning usable.
- All-local-data delete removes trip data/temporary exports and documents any time-bounded server/provider retention.
- Automated export/log/backup inspections find no prohibited provider payloads or secrets.

**US-09.2: Use the app accessibly.** As a traveller, I can use it with TalkBack or large text.

- Every essential drag operation has an alternative; targets >=48 dp; 200% font scaling has no clipped essential action.
- Map/list/progress state is understandable without color alone.
- NFR performance, crash, process-death, and reference-device targets have measured reports, not estimates labelled as results.

### E-10: Pilot and Release

**US-10.1: Validate usefulness.** As the owner, I can make a go/no-go decision from real use.

- Complete at least 10 observed planning sessions and five real outings; report failures and limitations.
- Capture actual paid-event counts, share-resolution outcomes, and navigation-launch success without location-content analytics.
- Pilot goals and unresolved bugs are reviewed before public launch.

**US-10.2: Ship honestly.** As a user, the listing matches the app.

- Working name cleared or replaced; privacy policy, Data Safety answers, SDK disclosures, and signing ownership verified.
- No claims of optimal scheduling, unlimited external navigation, guaranteed opening hours, or offline maps in the R1 listing.
- Required Play developer testing/verification is satisfied; rollback and routing kill-switch are demonstrated.

### Later Epics

**E-11 / R2: Deterministic scheduling.** Implement schema validation, directed matrix acquisition/budgets, OR-Tools constraints, independent schedule validation, proposal review, and honest outcome handling.

- No mandatory visit disappears; no completed visit is moved by a replan.
- Every proposed visit fits its entire verified opening interval; unknown data remains unknown.
- Daily anchors, drive limits, meal/rest rules, locks, and daylight constraints pass independent validation.
- The exact synthetic timing fixture in ARCHITECTURE.md passes; timeout is not misreported as infeasibility.
- Cost per solve and latency pass an approved threshold; managed optimization/provider alternative benchmark is complete.

**E-12 / R3: Live copilot.** Add only after R2 validation: explicit location consent, approved Android background execution, reliable progress source, deviation detection, and controlled replanning.

- No background collection before consent; revocation stops it promptly.
- Changes are proposals by default and preserve must-visits unless the user approves an exception.
- Maps URL launch is never treated as a telemetry feed or arrival callback.
- Battery, safety, privacy, and Play-policy tests pass before public availability.

## 6. Requirement Traceability

| PRD requirement | Primary epics | Test evidence |
|---|---|---|
| FR-01 | E-01, E-09 | Room lifecycle/migrations/process death |
| FR-02 | E-02, E-08 | SDK adapter/session and confirmation tests |
| FR-03 | E-03 | Intent lifecycle, URL corpus, SSRF/fallback tests |
| FR-04 | E-01, E-04 | Ordering transactions, duplicate visits, large collection tests |
| FR-05 | E-04, E-09 | Compose/map integration and accessibility |
| FR-06 | E-05, E-08 | Boundary/property tests, real route smoke, quota errors |
| FR-07 | E-06 | Physical-device launch, fallback, progress recovery |
| FR-08 | E-09 | Offline and retention/export inspections |
| FR-09 | E-07 | Schema round trip, adversarial imports, grant/expiry checks |
| FR-10 | E-09, E-10 | Permission/deletion/privacy release review |
| FR-11 | E-08 | Auth/attestation, key restrictions, kill-switch tests |
| NFR-01 through NFR-14 | E-00, E-08, E-09, E-10 | Pinned environment, measured device reports, policy gates |

## 7. Realistic Solo-Developer Plan

Assumption: one experienced Android developer, roughly 30-35 focused hours/week, reusable approved workstation tooling, and timely decisions on billing/legal matters. These estimates include integration and testing; AI assistance does not remove device testing or provider review.

| Window | Deliverable | Exit condition |
|---|---|---|
| Days 1-3 | Scope approval, 5 user interviews, competitor task walkthroughs, provider/legal questions | Decision log and spike acceptance agreed |
| Week 1 | Android skeleton, Room model, Maps/Places and handoff spikes, broker/auth spike | Real emulator/phone happy path and cost/limit evidence |
| Week 2 | Trips/days/stops, offline editing, drag/accessibility controls | Durable 50+ stop workflow |
| Week 3 | Search confirmation and Maps share intake/resolution fallback | Real share corpus passes with no silent wrong-place import |
| Week 4 | Ordered route batching, polylines, failure/stale states, server budgets | Complete 50-visit route and partial-failure evidence |
| Week 5 | Navigation progress/resume, text/file sharing/import | End-to-end journey and independent companion copy |
| Week 6 | Migration/security/privacy/performance/accessibility hardening | All required automated gates pass; no critical bugs |
| Weeks 7-8 | Private pilot and fixes | 10 sessions, five outings, measured value/cost, go/no-go review |
| Weeks 9-10 if proceeding | Release preparation and remaining polish | Signed AAB, store requirements, privacy and operational review |

Expected ranges: first limited internal APK in 10-15 working days; complete private-pilot MVP in 6-8 weeks; public-release readiness in roughly 8-10 weeks plus external policy/review delays. At 10-15 hours/week, plan approximately 16-24 calendar weeks rather than promising the full handover in a weekend.

R2 adds approximately 4-8 engineering weeks after its data/cost/licensing decisions. R3 is another substantial release, roughly 6-10+ weeks, not a checkbox on the MVP backlog. These ranges must be revised after the first two weeks of measured work.

### Fastest Safe Priority Order

1. Validate real Google Maps share and next-stop navigation behavior.
2. Make trip/stop persistence and manual order trustworthy.
3. Add route preview with complete/partial coverage and budget controls.
4. Finish sharing, recovery, and a real private pilot.
5. Spend on optimization only after users demonstrate that manual-day routing solves a real problem.

If the deadline shrinks, reduce launch audience/polish or call the output a prototype. Do not silently drop mandatory persistence, sharing, navigation fallback, or security while continuing to call it the specified MVP.

## 8. Test and Release Matrix

- Devices: approved ARM emulator with Google services plus at least one 4 GB physical phone and one contemporary Android release; verify current/minimum API coverage before release.
- Collections: empty, one stop, duplicated place, duplicated coordinate, 50/100/500 stops, 1,000-stop storage fixture, very long user labels, and Indian-script text.
- Networking: online, offline, timeout, cancellation after upstream dispatch, quota exceeded, authentication failure, app attestation failure, provider no-route and partial matrix errors.
- Lifecycle: rotation, foreground/background, cold share Intent, warm Intent, repeated delivery, process kill before/after save, navigation return, and interrupted import.
- Geography: city visits, a long road trip, island/disconnected destination, wrong entrance, inaccessible road, and maps-link ambiguity.
- Future scheduling: multiple windows, overnight opening, special closure, unknown hours, visit longer than opening, mandatory/optional conflict, driving caps, breaks, daylight, and solver timeouts.
- Privacy/licensing: logs, local database, backup, temporary files, exported JSON/text, attributions, and deletion all inspected using synthetic nonpersonal fixtures.
- Operational tests: negative key restriction, budget reservations under concurrency, kill switch, production/debug separation, and no secret in a release APK.

Go/no-go requires passing the PRD definition of done and the explicitly applicable gates above. A successful mocked test suite does not replace live SDK/Maps handoff evidence. No tests or implementation have been run as part of this documentation delivery.

## 9. Handover Coverage

| Requested output | Location |
|---|---|
| 1. Engineering-ready PRD | PRD sections 1-8 |
| 2. Exact MVP and exclusions | PRD section 3 |
| 3. Functional/non-functional requirements | PRD sections 4 and 6 |
| 4. Flows, screens, navigation | PRD section 5 |
| 5. System architecture | ARCHITECTURE sections 1-2 |
| 6. Database and API contracts | ARCHITECTURE sections 3-4 |
| 7. Routing 50+ stops | ARCHITECTURE sections 5-6 |
| 8. Scheduling/optimization | ARCHITECTURE section 7 |
| 9. Google limits, cost, licensing | This document sections 2-4 and sources; architecture data/security boundaries |
| 10. Epics/stories/acceptance | This document sections 5-6 and 8 |
| 11. Solo plan | This document section 7 |

## 10. Official Sources

All accessed 2026-11-09. These are evidence links, not a guarantee of contractual permission or future pricing.

- S1: [Routes usage, billing, quotas and waypoint limit](https://developers.google.com/maps/documentation/routes/usage-and-billing)
- S2: [Google Maps URLs: navigation, waypoint support and URL length](https://developers.google.com/maps/documentation/urls/guide)
- S3: [Compute Route Matrix: element/waypoint limits and error handling](https://developers.google.com/maps/documentation/routes/compute_route_matrix)
- S4: [Routes, Route Optimization and Navigation SKU billing triggers](https://developers.google.com/maps/billing-and-pricing/sku-details/routes)
- S5: [Current global price list](https://developers.google.com/maps/billing-and-pricing/pricing)
- S6: [Current India price list](https://developers.google.com/maps/billing-and-pricing/pricing-india)
- S7: [India eligibility and billing FAQ](https://developers.google.com/maps/billing-and-pricing/india)
- S8: [Places SDK Android policies: storage, Place IDs and attribution](https://developers.google.com/maps/documentation/places/android-sdk/policies)
- S9: [Routes policies: storage and attribution](https://developers.google.com/maps/documentation/routes/policies)
- S10: [Maps Platform service-specific terms](https://cloud.google.com/maps-platform/terms/maps-service-terms)
- S11: [API key security, authenticated proxy and mobile SDK guidance](https://developers.google.com/maps/api-security-best-practices)
- S12: [Places Android Autocomplete session pricing](https://developers.google.com/maps/documentation/places/android-sdk/session-pricing)
- S13: [Places Android usage and billing](https://developers.google.com/maps/documentation/places/android-sdk/usage-and-billing)
- S14: [Place Details New field-to-SKU mapping](https://developers.google.com/maps/documentation/places/web-service/place-details)

Implementation references to review when development begins: [Android share reception](https://developer.android.com/training/sharing/receive), [Maps Intents for Android](https://developer.android.com/guide/components/google-maps-intents), [Maps Compose](https://developers.google.com/maps/documentation/android-sdk/maps-compose), and [OR-Tools vehicle routing with time windows](https://developers.google.com/optimization/routing/vrptw).

## 11. Immediate Next Decision

Approve or change this proposed R1 baseline: Android-only, private pilot, local-only trips, manual day/order, Google Maps handoff, small authenticated routing broker, no automatic scheduling, and no paid billing activation until an explicit budget/licensing decision.

After approval, the next engineering task is the short Android/Google integration spike in E-00, not an LLM agent or full optimizer. Confirm the Android bundle path, personal application ID/signing owner, Google billing eligibility, and permitted workstation use before project setup.

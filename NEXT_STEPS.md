# Next Steps: AI and In-App Navigation

## Current status

The optional in-app Navigation SDK integration is in source but has not been built or tested yet: this machine could not reach Google's Maven repository. The installed emulator app remains v0.4. Build and install v0.5 only after the SDK dependency resolves and a restricted Google key is configured.

## Before using either provider

RouteWise works without these keys. Groq AI is optional and disabled by default. Google in-app navigation is optional; the existing OpenStreetMap route preview and Google Maps handoff remain available.

## Groq AI key

1. On the machine where you will run RouteWise, open **Map settings**.
2. Under **AI break planner**, paste your Groq API key, confirm the model ID, and enable Groq AI.
3. Save settings. The key is encrypted with Android Keystore on that device and is not included in trip exports or backups.
4. In the day planner, find nearby OSM places for a planned meal/rest break, then tap **Rank with AI**. Read and accept the per-request disclosure before sending.

Groq only ranks the supplied map candidates; it cannot add a venue to the trip. Reasons, access, hours, safety, facilities, and suitability remain unverified. A local limit of 12 requests/day is not a provider billing cap.

## Google Navigation SDK key

This project uses Google's **Navigation SDK for Android** for the optional in-app turn-by-turn screen. It does not need a separate Routes API key. The SDK calculates a fresh route from the device's current location to the remaining day destinations. That route can differ from the existing OSM preview.

1. Create or choose a Google Cloud project and link a billing account.
2. Enable **Navigation SDK for Android** for that project.
3. Create an API key and restrict it to **Navigation SDK for Android**. Add an Android application restriction for this app's package name and signing-certificate SHA-1 fingerprint. Use the debug fingerprint for local builds; configure separate restrictions for release signing before publishing.
   - Package for local debug builds: `app.routewise`.
   - Get the local debug certificate fingerprint with `./gradlew :app:signingReport` and copy the SHA-1 shown for the `debug` variant.
   - A release build uses a different signing certificate and needs a separate matching restriction.
4. At the RouteWise project root, create a local `secrets.properties` file (it is gitignored) containing:

   ```properties
   GOOGLE_NAVIGATION_API_KEY=YOUR_RESTRICTED_KEY
   ```

5. Build and install the normal app, without the isolated-test flag:

   ```sh
   ./gradlew :app:assembleDebug :app:installDebug
   ```

The key is embedded in the installed APK, as required for a native SDK. Package/SHA-1 and API restrictions are therefore important; never use an unrestricted key or commit `secrets.properties`.

Before distribution, include the Navigation SDK's required attribution and open-source notices (`NOTICE.txt` and `LICENSES.txt`) in the app's legal notices/About screen, as required by Google's setup documentation.

## Usage and budget safeguards

The current Google India pricing page lists a **7,000 Navigation Request free usage cap per month**. Navigation usage is counted by destination in navigation route requests, not by app launches or map views. A day with four remaining destinations can consume four destination units when sent to Google. The current listed over-cap rate is **$8 per 1,000 destinations**, then **$2 per 1,000** above the next volume tier. Prices, eligibility, exchange rates, and taxes can change; confirm them in the billing account before enabling navigation.

The Navigation SDK requires billing even when usage stays under the free cap. The free allowance is not a guaranteed spend stop. Set a small Google Cloud budget with email alerts and review SKU-level usage. Budget alerts are notifications, not a hard spending cap. If you need a guaranteed $0 ceiling, do not enable Google in-app navigation; continue using the OSM preview and external Google Maps handoff.

For an immediate hard stop after testing, disable **Navigation SDK for Android** in the Cloud project or remove/rotate the local key; budget alerts alone will not stop requests. The app does not set a billing hard cap on your Google account.

No Google request is made until the user chooses **In app**, reviews the disclosure, and continues. Precise location is requested at that point. Google receives the device location and confirmed destination coordinates. Navigation stops when the user ends the session or closes this screen. Google Navigation terms must also be accepted the first time.

The SDK can render only on its own Google map surface; it cannot be layered onto RouteWise's MapLibre map. Navigation needs internet access and an Android device/emulator with Google Play services. No API key has been added to this source tree, and the real Google navigation route has not been exercised yet.

## References

- [Google Navigation SDK setup](https://developers.google.com/maps/documentation/navigation/android-sdk/get-api-key)
- [Google Navigation SDK usage and billing](https://developers.google.com/maps/documentation/navigation/android-sdk/usage-and-billing)
- [Google Maps Platform India pricing](https://developers.google.com/maps/billing-and-pricing/pricing-india#navigation-sdk-pricing)
- [Google Navigation SDK route setup](https://developers.google.com/maps/documentation/navigation/android-sdk/route)
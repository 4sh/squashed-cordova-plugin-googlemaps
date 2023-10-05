# Cordova GoogleMaps plugin for Android, iOS and Browser v2.7.2

This is a maintained fork of https://github.com/mapsplugin/cordova-plugin-googlemaps.

There's also [another repository](https://github.com/4sh/squashed-cordova-plugin-googlemaps) that contains only snapshot versions
(commits were squashed to reduce history and improve installation time) currently used by multiple apps (some with 100k+ users) running in production.

## Release Notes

   - **v2.7.2** (Oct 5, 2023)
    - Fix: (iOS) Styles option is now taken into account before initializing the map (redraw is no longer needed when using a custom style)

  - **v2.7.1.3** (Dec 22, 2021)
    - Fix: (Android) Custom markers are now working with `cordova-android` 10+
    - Fix: (Android) Blank map when starting app using `cordova-android` with AGP version from 4.1.3
    - Fix: (JS bridge) Remove calls to `getMessage()` as this method does not exist on error object

  - **v2.7.1.2** (Nov 5, 2021)
    - Fix: (Android) Plugin is now compatible with `cordova-plugin-geolocation`

  - **v2.7.1.1** (Aug 26, 2020)
    - Chore: (iOS) Improve installation time of Google Maps SDK for iOS 

  - **v2.7.0**
    - Re-adoption: <a href="https://github.com/mapsplugin/cordova-plugin-googlemaps-sdk" target="_blank">cordova-plugin-googlemaps-sdk dependency</a>
    - Important update: No longer support `UIWebView` on iOS. `WKWebView` only.
    - Fix: (iOS) Can't load image files from local host on ionic 4 / 5
    - Update: (Android) prevent null pointer error in AsyncLoadImage.java
    - Fix: Css animation interference when call setDiv and there is a push/pop page
    - Fix: (Android/iOS/Browser) KML parser crash
    - Fix: flickering and wrong rendering of some DOM elements
    - Add: `map.stopAnimation()`
    - Fix: can't remove map while map.animateCamera() is running
    - Update: (Android) Increase cache memory size
    - Update: (Android/iOS) Danish localization
    - Fix: (Android) Prevent resize event after `map.setDiv(null)`
    - Fix: (Android/iOS) Can not interactive with the map inside <form>
    - Fix: jslint errors
    - Fix: marker.setIcon crashes
    - Update: Set default value range to heading and tilt
    - Fix: (Android/iOS) touch detection is wrong after clicking on back button very soon.
    - Fix: An error occurs when you click a marker of marker cluster #2660
    - Remove promise-7.0.4-min.js.map
    - Fix: (iOS) bug fix: App crashes if "bearing" property is "<null>"
    - Fix: HTMLColor2RGBA() converts to incorrect value
    - Fix: (Android) Can't load marker image from the Internet
    - many bug fixes...

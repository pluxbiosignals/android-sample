[![API](https://img.shields.io/badge/API-19%2B-green.svg?style=flat)](https://android-arsenal.com/api?level=19)

# PLUX API Android Sample App  #
-----

Working example of PLUX's Android API.

## Prerequisites ##
- Android Studio or IntelliJ IDEA
- Android SDK (build-tools 28.0.0 and SDK 33)

## Supported Devices ##
- [biosignalsplux](https://support.pluxbiosignals.com/knowledge-base/getting-started-biosignalsplux/)
- [BITalino](https://support.pluxbiosignals.com/knowledge-base/bitalino-documentation/)
- [muscleBAN](https://support.pluxbiosignals.com/knowledge-base/muscleban-getting-started/)
- [biosignalplux solo](https://www.pluxbiosignals.com/collections/biosignalsplux/products/solo-kit)
- [cardioban](https://support.pluxbiosignals.com/knowledge-base/cardioban-getting-started/)


## How-to use the library in your projects ##

1. In your root-level (project-level) Gradle file add the repository URL link to the repositories block:
```gradle
repositories {
        // ... other dependencies
        maven { url "https://codeberg.org/api/packages/MavenPLUX/maven" }
    }
```

2. In your module (app-level) Gradle file add the dependency for the PLUX API Android library.
```gradle
dependencies {
    // ... other dependencies
    implementation 'info.plux.api:api:1.5.2'
}
```
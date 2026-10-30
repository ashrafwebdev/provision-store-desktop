# Provision Store Desktop

Windows desktop app for the provision store: day book, billing, items and stock, and khata (credit).
It works with the same data layout as the Android app, so a backup from the phone can be restored here.

## Install

1. Open the [Releases](https://github.com/ashrafwebdev/provision-store-desktop/releases) page.
2. Download the newest `ProvisionStore-v1.<n>.0.msi` and run it.
3. Windows may show a SmartScreen warning because the installer is not code-signed. Choose "More info", then "Run anyway".

## Where the data lives

The shop's database is kept in `%APPDATA%\ProvisionStore\provision.db`. Installing a new version does not touch it.

## Updates

The app checks GitHub each time it opens. When a newer installer exists, it offers to download and install it.

## Build

Requires JDK 17 or newer with `jpackage` (JDK 17 from Temurin works).

```bash
./gradlew test
./gradlew packageMsi -PappVersion=1.0.0
```

The installer is written to `build/compose/binaries/main/msi/`.

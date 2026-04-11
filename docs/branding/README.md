# Finalis Mobile Branding

This folder mirrors the canonical Finalis branding assets from `finalis-core/branding` for mobile implementation work.

Selected assets for Android V1:

- `finalis-app-icon.svg`
  - used as the launcher icon source
  - best fit for packaged app identity because it is already a square application mark
- `finalis-symbol.svg`
  - used for in-app light surfaces
  - best fit for wallet headers and identity blocks because it is monochrome and minimal
- `finalis-symbol-dark.svg`
  - used for dark brand surfaces inside the app
  - best fit for the primary hero panel and loading/identity treatments

Assets intentionally not used as primary in-app marks:

- `finalis-logo-horizontal.svg`
  - contains the `finalis-core` wordmark, which is less suitable as the main wallet product label inside the Android UI
- `finalis-logo-stacked.svg`
  - same reason as above
- `finalis-splash-lockup.svg`
  - reserved for possible future splash-specific use
- `finalis-token-badge.svg`
  - less suitable than the symbol for a conservative wallet UI

Android rasterized copies are generated under `android/app/src/main/res/drawable-nodpi/` for launcher and Compose image use.

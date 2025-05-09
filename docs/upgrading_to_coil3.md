# Upgrading to Coil 3.x

Coil 3 is the next major version of Coil that has a number of major improvements:

- Full support for [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/) including all major targets (Android, iOS, JVM, JS, and [WASM](https://coil-kt.github.io/coil/sample/)).
- Support for multiple networking libraries (Ktor and OkHttp). Alternatively, Coil can be used without a network dependency if you only need to load local/static files.
- Improved Compose `@Preview` rendering and support for custom preview behavior via `LocalAsyncImagePreviewHandler`.
- Important fixes for bugs that required breaking existing behaviour (outlined below).

This document provides a high-level overview of the main changes from Coil 2 to Coil 3 and highlights any breaking or important changes. It does not cover every binary incompatible change or small behaviour changes.

Using Coil 3 in a Compose Multiplatform project? Check out the [`samples`](https://github.com/coil-kt/coil/tree/3.x/samples/compose) repository for examples.

## Maven Coordinates and Package Name

Coil's Maven coordinates were updated from `io.coil-kt` to `uz.rsteam.coil-3` and its package name was updated from `coil` to `coil3`. This allows Coil 3 to run side by side with Coil 2 without binary compatibility issues. For example, `io.coil-kt:coil:2.7.0` is now `uz.rsteam.coil-3:coil:3.0.0`.

The `coil-base` and `coil-compose-base` artifacts were renamed to `coil-core` and `coil-compose-core` respectively to align with the naming conventions used by Coroutines, Ktor, and AndroidX.

## Network Images

**`coil-core` no longer supports loading images from the network by default.** [You must add a dependency on one of Coil's network artifacts. See here for more info.](network.md). This was changed so consumers could use different networking libraries or avoid a network dependency if their app doesn't need it.

Additionally, cache control headers are no longer respected by default. See [here](network.md) for more info.

## Multiplatform

Coil 3 is now a Kotlin Multiplatform library that supports Android, JVM, iOS, macOS, Javascript, and WASM.

On Android, Coil uses the standard graphics classes to render images. On non-Android platforms, Coil uses [Skiko](https://github.com/JetBrains/skiko) to render images. Skiko is a set of Kotlin bindings that wrap the [Skia](https://github.com/google/skia) graphics engine developed by Google.

As part of decoupling from the Android SDK, a number of API changes were made. Notably:

- `Drawable` was replaced with a custom `Image` interface. Use `Drawable.asImage()` and `Image.asDrawable(resources)` to convert between the classes on Android. On non-Android platforms use `Bitmap.asImage()` and `Image.toBitmap()`.
- Usages of Android's `android.net.Uri` class were replaced a multiplatform `coil3.Uri` class. Any call sites that pass `android.net.Uri` as `ImageRequest.data` are unaffected. Custom `Fetcher`s that rely on receiving an `android.net.Uri` will need to be updated to use `coil3.Uri`.
- Usages of `Context` were replaced with `PlatformContext`. `PlatformContext` is a type alias for `Context` on Android and can be accessed using `PlatformContext.INSTANCE` on non-Android platforms. Use `LocalPlatformContext.current` to get a reference in Compose Multiplatform.
- The `Coil` class was renamed to `SingletonImageLoader`.
- If you're implementing `ImageLoaderFactory` in your custom Android `Application` class, you'll need to switch to implementing `SingletonImageLoader.Factory` as a replacement for `ImageLoaderFactory`. Once you implement `SingletonImageLoader.Factory`, you'll be able to override `newImageLoader()` if you need or want to override it.

The `coil-svg` artifact is supported in multiplatform, but the `coil-gif` and `coil-video` artifacts continue to be Android-only (for now) as they rely on specific Android decoders and libraries.

## Compose

The `coil-compose` artifact's APIs are mostly unchanged. You can continue using `AsyncImage`, `SubcomposeAsyncImage`, and `rememberAsyncImagePainter` the same way as with Coil 2. Additionally, this methods have been updated to be [restartable and skippable](https://developer.android.com/jetpack/compose/performance/stability) which should improve their performance.

- `AsyncImagePainter.state` is now a `StateFlow`. It should be observed using `val state = painter.state.collectAsState()`.
- `AsyncImagePainter`'s default `SizeResolver` no longer waits for the first `onDraw` call to get the size of the canvas. Instead, `AsyncImagePainter` defaults to `Size.ORIGINAL`.
- The Compose `modelEqualityDelegate` delegate is now set via a composition local, `LocalAsyncImageModelEqualityDelegate`, instead of as a parameter to `AsyncImage`/`SubcomposeAsyncImage`/`rememberAsyncImagePainter`.

## General

Other important behavior changes include:

- First party `Fetcher`s and `Decoder`s (e.g. `NetworkFetcher.Factory`, `SvgDecoder`, etc.) are now automatically added to each new `ImageLoader` through a service loader. This behaviour can be disabled with `ImageLoader.Builder.serviceLoaderEnabled(false)`.
- Remove support for `android.resource://example.package.name/drawable/image` URIs as it prevents resource shrinking optimizations. It's recommended to pass `R.drawable.image` values directly. Passing the resource ID instead of the resource name will still work: `android.resource://example.package.name/12345678`. If you still needs its functionality you can [manually include `ResourceUriMapper` in your component registry](https://github.com/coil-kt/coil/blob/da7d872e340430014dbc5136e35eb62f9b17662e/coil-core/src/androidInstrumentedTest/kotlin/coil3/map/ResourceUriMapper.kt).
- A file's last write timestamp is no longer added to its cache key by default. This is to avoid reading the disk on the main thread (even for a very short amount of time). This can be re-enabled with `ImageRequest.Builder.addLastModifiedToFileCacheKey(true)` or `ImageLoader.Builder.addLastModifiedToFileCacheKey(true)`.
- Output image dimensions are now enforced to be less than 4096x4096 to guard against accidental OOMs. This can be configured with `ImageLoader/ImageRequest.Builder.maxBitmapSize`. To disable this behavior set `maxBitmapSize` to `Size.ORIGINAL`.
- Coil 2's `Parameters` API was replaced by `Extras`. `Extras` don't require a string key and instead rely on identity equality. `Extras` don't support modifying the memory cache key. Instead, use `ImageRequest.memoryCacheKeyExtra` if your extra affects the memory cache key.
- Many `ImageRequest.Builder` functions have moved to be extension functions to more easily support multiplatform.

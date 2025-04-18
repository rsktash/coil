package coil3.compose

import android.graphics.BitmapFactory
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BrushPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toDrawable
import coil3.Bitmap
import coil3.BitmapImage
import coil3.Extras
import coil3.ImageLoader
import coil3.asImage
import coil3.compose.AsyncImagePainter.State
import coil3.compose.core.test.R
import coil3.decode.DecodeUtils
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Scale
import coil3.size.Size
import coil3.test.utils.ComposeTestActivity
import coil3.test.utils.context
import coil3.transform.Transformation
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalAtomicApi::class)
class AsyncImageTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>()

    private lateinit var requestTracker: ImageLoaderIdlingResource
    private lateinit var imageLoader: ImageLoader

    @Before
    fun before() {
        requestTracker = ImageLoaderIdlingResource()
        imageLoader = ImageLoader.Builder(composeTestRule.activity)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .eventListener(requestTracker)
            .components {
                add(FakeNetworkFetcher.Factory())
            }
            .build()
        composeTestRule.registerIdlingResource(requestTracker)
    }

    @After
    fun after() {
        composeTestRule.unregisterIdlingResource(requestTracker)
        imageLoader.shutdown()
    }

    @Test
    fun fixedSize() {
        assumeSupportsCaptureToImage()

        composeTestRule.setContent {
            AsyncImage(
                model = "https://example.com/image",
                contentDescription = null,
                imageLoader = imageLoader,
                modifier = Modifier
                    .size(128.dp, 166.dp)
                    .testTag(Image),
            )
        }

        waitForRequestComplete()

        assertLoadedBitmapSize(128.dp.toPx().toInt(), 166.dp.toPx().toInt())

        composeTestRule.onNodeWithTag(Image)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(128.dp)
            .assertHeightIsEqualTo(166.dp)
            .captureToImage()
            .assertIsSimilarTo(R.drawable.sample)
    }

    @Test
    fun fillMaxWidth_boundedConstraint() {
        assumeSupportsCaptureToImage()

        composeTestRule.setContent {
            AsyncImage(
                model = "https://example.com/image",
                contentDescription = null,
                imageLoader = imageLoader,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(Image),
            )
        }

        waitForRequestComplete()

        val expectedWidthPx = displaySize.width.toDouble()
        val expectedHeightPx = expectedWidthPx * SampleHeight / SampleWidth

        assertSampleLoadedBitmapSize(expectedWidthPx, expectedHeightPx)

        composeTestRule.onNodeWithTag(Image)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(expectedWidthPx.toDp())
            .assertHeightIsEqualTo(expectedHeightPx.toDp())
            .captureToImage()
            .assertIsSimilarTo(R.drawable.sample)
    }

    @Test
    fun fillMaxWidth_infiniteConstraint() {
        assumeSupportsCaptureToImage()

        composeTestRule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AsyncImage(
                    model = "https://example.com/image",
                    contentDescription = null,
                    imageLoader = imageLoader,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(Image),
                )
            }
        }

        waitForRequestComplete()

        val expectedWidthPx = displaySize.width.toDouble()
        val expectedHeightPx = expectedWidthPx * SampleHeight / SampleWidth

        assertSampleLoadedBitmapSize(expectedWidthPx, expectedHeightPx)

        composeTestRule.onNodeWithTag(Image)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(expectedWidthPx.toDp())
            .assertHeightIsEqualTo(expectedHeightPx.toDp())
            .captureToImage()
            .assertIsSimilarTo(R.drawable.sample)
    }

    @Test
    fun fillMaxHeight() {
        assumeSupportsCaptureToImage()

        composeTestRule.setContent {
            AsyncImage(
                model = "https://example.com/image",
                contentDescription = null,
                imageLoader = imageLoader,
                modifier = Modifier
                    .fillMaxHeight()
                    .testTag(Image),
            )
        }

        waitForRequestComplete()

        val expectedHeightPx = displaySize.height.toDouble()
        val expectedWidthPx = (expectedHeightPx * SampleWidth / SampleHeight)
            .coerceAtMost(displaySize.width.toDouble())

        assertSampleLoadedBitmapSize(expectedWidthPx, expectedHeightPx)

        composeTestRule.onNodeWithTag(Image)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(expectedWidthPx.toDp())
            .assertHeightIsEqualTo(expectedHeightPx.toDp())
            .captureToImage()
            .assertIsSimilarTo(R.drawable.sample, threshold = 0.85)
    }

    @Test
    fun dynamicSize() {
        assumeSupportsCaptureToImage()

        composeTestRule.setContent {
            AsyncImage(
                model = "https://example.com/image",
                contentDescription = null,
                imageLoader = imageLoader,
                modifier = Modifier
                    .testTag(Image),
            )
        }

        waitForRequestComplete()

        val expectedWidthPx = displaySize.width.toDouble().coerceAtMost(SampleWidth.toDouble())
        val expectedHeightPx = expectedWidthPx * SampleHeight / SampleWidth

        assertSampleLoadedBitmapSize(expectedWidthPx, expectedHeightPx)

        composeTestRule.onNodeWithTag(Image)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(expectedWidthPx.toDp())
            .assertHeightIsEqualTo(expectedHeightPx.toDp())
            .captureToImage()
            .assertIsSimilarTo(R.drawable.sample)
    }

    @Test
    fun dynamicHeight() {
        assumeSupportsCaptureToImage()

        composeTestRule.setContent {
            LazyColumn(
                content = {
                    item {
                        AsyncImage(
                            model = "https://example.com/image",
                            contentDescription = null,
                            imageLoader = imageLoader,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.testTag(Image),
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(fraction = 0.5f),
            )
        }

        waitForRequestComplete()

        val expectedWidthPx = (displaySize.width / 2.0).coerceAtMost(SampleWidth.toDouble())
        val expectedHeightPx = expectedWidthPx * SampleHeight / SampleWidth

        assertSampleLoadedBitmapSize(expectedWidthPx, expectedHeightPx)

        composeTestRule.onNodeWithTag(Image)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(expectedWidthPx.toDp(), tolerance = 1.dp)
            .assertHeightIsEqualTo(expectedHeightPx.toDp(), tolerance = 1.dp)
            .captureToImage()
            .assertIsSimilarTo(R.drawable.sample)
    }

    @Test
    fun overwriteLoading() {
        assumeSupportsCaptureToImage()

        // Remove this or `setContent` will timeout.
        composeTestRule.unregisterIdlingResource(requestTracker)

        composeTestRule.setContent {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("https://example.com/image")
                    .fetcherFactory(LoadingFetcher.Factory())
                    .build(),
                contentDescription = null,
                imageLoader = imageLoader,
                modifier = Modifier
                    .size(128.dp, 128.dp)
                    .testTag(Image),
                loading = {
                    Box(
                        modifier = Modifier
                            .background(color = Color.Blue),
                    )
                },
            )

            Spacer(
                modifier = Modifier
                    .height(8.dp)
                    .fillMaxWidth(),
            )

            Box(
                modifier = Modifier
                    .size(128.dp, 128.dp)
                    .background(color = Color.Blue)
                    .testTag(Loading),
            )
        }

        composeTestRule.waitUntil(10_000) {
            requestTracker.startedRequests >= 1
        }

        val actual = composeTestRule.onNodeWithTag(Image).captureToImage()
        val expected = composeTestRule.onNodeWithTag(Loading).captureToImage()
        actual.assertIsSimilarTo(expected)
    }

    @Test
    fun overwriteError() {
        assumeSupportsCaptureToImage()

        composeTestRule.setContent {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("https://example.com/image")
                    .fetcherFactory(ErrorFetcher.Factory())
                    .build(),
                contentDescription = null,
                imageLoader = imageLoader,
                modifier = Modifier
                    .size(128.dp, 128.dp)
                    .testTag(Image),
                error = {
                    Box(
                        modifier = Modifier
                            .background(color = Color.Blue),
                    )
                },
            )

            Spacer(
                modifier = Modifier
                    .height(8.dp)
                    .fillMaxWidth(),
            )

            Box(
                modifier = Modifier
                    .size(128.dp, 128.dp)
                    .background(color = Color.Blue)
                    .testTag(Error),
            )
        }

        waitForRequestComplete()

        val actual = composeTestRule.onNodeWithTag(Image).captureToImage()
        val expected = composeTestRule.onNodeWithTag(Error).captureToImage()
        actual.assertIsSimilarTo(expected)
    }

    @Test
    fun overwriteSuccess() {
        assumeSupportsCaptureToImage()

        composeTestRule.setContent {
            SubcomposeAsyncImage(
                model = "https://example.com/image",
                contentDescription = null,
                imageLoader = imageLoader,
                modifier = Modifier
                    .size(128.dp, 128.dp)
                    .testTag(Image),
                success = {
                    Box(
                        modifier = Modifier
                            .background(color = Color.Blue),
                    )
                },
            )

            Spacer(
                modifier = Modifier
                    .height(8.dp)
                    .fillMaxWidth(),
            )

            Box(
                modifier = Modifier
                    .size(128.dp, 128.dp)
                    .background(color = Color.Blue)
                    .testTag(Success),
            )
        }

        waitForRequestComplete()

        val actual = composeTestRule.onNodeWithTag(Image).captureToImage()
        val expected = composeTestRule.onNodeWithTag(Success).captureToImage()
        actual.assertIsSimilarTo(expected)
    }

    @Test
    fun overwriteContent() {
        assumeSupportsCaptureToImage()

        var compositions = 0
        var maxCompositions = 2

        composeTestRule.setContent {
            SubcomposeAsyncImage(
                model = "https://example.com/image",
                contentDescription = null,
                imageLoader = imageLoader,
                modifier = Modifier
                    .size(100.dp)
                    .testTag(Image),
            ) {
                val state by painter.state.collectAsState()
                val currentComposition = compositions++
                if (currentComposition < maxCompositions) {
                    assertTrue {
                        (currentComposition == 0 && state is State.Loading) || state is State.Success
                    }
                    if (state is State.Success) {
                        // No more subsequent compositions should happen.
                        maxCompositions = -1
                    }
                } else {
                    fail("Recomposed too many times. State: $state")
                }
                SubcomposeAsyncImageContent(
                    modifier = Modifier.clip(CircleShape),
                )
            }

            Spacer(
                modifier = Modifier
                    .height(8.dp)
                    .fillMaxWidth(),
            )

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .testTag(Content),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.sample),
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape),
                )
            }
        }

        waitForRequestComplete()

        val actual = composeTestRule.onNodeWithTag(Image).captureToImage()
        val expected = composeTestRule.onNodeWithTag(Content).captureToImage()
        actual.assertIsSimilarTo(expected)
    }

    @Test
    fun listener() {
        assumeSupportsCaptureToImage()

        composeTestRule.setContent {
            val value = "" // Use a fake value inside the listener to make it stateful.
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("https://example.com/image")
                    // Ensure this doesn't constantly recompose or restart image requests.
                    .listener { _, _ -> value + "" }
                    .build(),
                contentDescription = null,
                imageLoader = imageLoader,
                modifier = Modifier
                    .testTag(Image),
            )
        }

        waitForRequestComplete()

        val expectedWidthPx = displaySize.width.toDouble().coerceAtMost(SampleWidth.toDouble())
        val expectedHeightPx = expectedWidthPx * SampleHeight / SampleWidth

        assertSampleLoadedBitmapSize(expectedWidthPx, expectedHeightPx)

        composeTestRule.onNodeWithTag(Image)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(expectedWidthPx.toDp())
            .assertHeightIsEqualTo(expectedHeightPx.toDp())
            .captureToImage()
            .assertIsSimilarTo(R.drawable.sample)
    }

    @Test
    fun fillMaxSize_scaleFill() {
        assumeSupportsCaptureToImage()

        composeTestRule.setContent {
            AsyncImage(
                model = "https://example.com/image",
                contentDescription = null,
                imageLoader = imageLoader,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(Image),
                contentScale = ContentScale.Crop,
            )
        }

        waitForRequestComplete()

        val expectedWidthPx = displaySize.width.toDouble()
        val expectedHeightPx = displaySize.height.toDouble()

        assertSampleLoadedBitmapSize(expectedWidthPx, expectedHeightPx, scale = Scale.FILL)

        composeTestRule.onNodeWithTag(Image)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(expectedWidthPx.toDp())
            .assertHeightIsEqualTo(expectedHeightPx.toDp())
            .captureToImage()
            .assertIsSimilarTo(R.drawable.sample, scale = Scale.FILL)
    }

    @Test
    fun doesNotRecompose() {
        val compositionCount = AtomicInt(0)

        composeTestRule.setContent {
            compositionCount.fetchAndIncrement()
            AsyncImage(
                model = "https://example.com/image",
                contentDescription = null,
                imageLoader = imageLoader,
            )
        }

        waitForRequestComplete()

        assertEquals(1, compositionCount.load())
    }

    @Test
    fun painterState_notMemoryCached() {
        val outerCompositionCount = AtomicInt(0)
        val innerCompositionCount = AtomicInt(0)

        composeTestRule.setContent {
            outerCompositionCount.fetchAndIncrement()
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("https://example.com/image")
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .coroutineContext(EmptyCoroutineContext)
                    .build(),
                contentDescription = null,
                imageLoader = imageLoader,
            ) {
                innerCompositionCount.fetchAndIncrement()
                assertIs<State.Success>(painter.state.collectAsState().value)
                SubcomposeAsyncImageContent()
            }
        }

        waitForRequestComplete()

        assertEquals(1, outerCompositionCount.load())
        assertEquals(1, innerCompositionCount.load())
    }

    @Test
    fun painterState_memoryCached() {
        val url = "https://example.com/image"
        val bitmap = BitmapFactory
            .decodeResource(composeTestRule.activity.resources, R.drawable.sample)
            .toDrawable(composeTestRule.activity.resources)
            .asImage()
        imageLoader.memoryCache!![MemoryCache.Key(url)] = MemoryCache.Value(bitmap)

        val outerCompositionCount = AtomicInt(0)
        val innerCompositionCount = AtomicInt(0)

        composeTestRule.setContent {
            outerCompositionCount.fetchAndIncrement()
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = null,
                imageLoader = imageLoader,
            ) {
                innerCompositionCount.fetchAndIncrement()
                assertIs<State.Success>(painter.state.collectAsState().value)
                SubcomposeAsyncImageContent()
            }
        }

        waitForRequestComplete()

        assertEquals(1, outerCompositionCount.load())
        assertEquals(1, innerCompositionCount.load())
    }

    /** Regression test: https://github.com/coil-kt/coil/issues/1133 */
    @Test
    fun infiniteConstraint() {
        assumeSupportsCaptureToImage()

        val expectedWidthDp = 32.dp

        composeTestRule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AsyncImage(
                    model = "https://example.com/image",
                    contentDescription = null,
                    imageLoader = imageLoader,
                    modifier = Modifier
                        .width(expectedWidthDp)
                        .testTag(Image),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        waitForRequestComplete()

        val expectedWidthPx = expectedWidthDp.toPx().toDouble()
        val expectedHeightPx = expectedWidthPx * SampleHeight / SampleWidth

        assertSampleLoadedBitmapSize(expectedWidthPx, expectedHeightPx)

        composeTestRule.onNodeWithTag(Image)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(expectedWidthPx.toDp())
            .assertHeightIsEqualTo(expectedHeightPx.toDp())
            .captureToImage()
            .assertIsSimilarTo(R.drawable.sample, threshold = 0.85)
    }

    /** Regression test: https://github.com/coil-kt/coil/issues/1217 */
    @Test
    fun noneContentScaleShouldLoadAtOriginalSize() {
        composeTestRule.setContent {
            AsyncImage(
                model = "https://example.com/image",
                contentDescription = null,
                imageLoader = imageLoader,
                modifier = Modifier.width(30.dp),
                contentScale = ContentScale.None,
            )
        }

        waitForRequestComplete()

        assertLoadedBitmapSize(SampleWidth, SampleHeight)
    }

    /** Regression test: https://github.com/coil-kt/coil/issues/1821 */
    @Test
    fun painterWithOneDimensionInfiniteConstraint() {
        assumeSupportsCaptureToImage()

        val expectedSize = 32.dp

        composeTestRule.setContent {
            AsyncImage(
                model = Unit,
                contentDescription = null,
                imageLoader = imageLoader,
                error = BrushPainter(
                    Brush.verticalGradient(
                        0.0f to Color.Red,
                        0.5f to Color.Blue,
                        1.0f to Color.Green,
                    ),
                ),
                modifier = Modifier
                    .size(expectedSize)
                    .testTag(Image),
                contentScale = ContentScale.Crop,
            )
        }

        waitForRequestComplete()

        composeTestRule.onNodeWithTag(Image)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(expectedSize)
            .assertHeightIsEqualTo(expectedSize)
            .captureToImage()
            .assertIsSimilarTo(R.drawable.vertical_gradient)
    }

    @Test
    fun requestWithUnstableParamsComposesAtMostOnce() {
        val tickerFlow = flow {
            var count = 0
            while (true) {
                emit(count++)
                delay(50.milliseconds)
            }
        }
        val compositionCount = AtomicInt(0)
        val onStartCount = AtomicInt(0)

        composeTestRule.setContent {
            val count by tickerFlow.collectAsState(0)

            compositionCount.fetchAndIncrement()

            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("https://example.com/image")
                    .listener(
                        onStart = {
                            onStartCount.fetchAndIncrement()
                        },
                    )
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                imageLoader = imageLoader,
            )

            BasicText("Count: $count")
        }

        waitUntil { compositionCount.load() >= 10 }

        assertEquals(1, onStartCount.load())
    }

    @Test
    fun newRequestStartsWhenDataChanges() {
        val tickerFlow = flow {
            var count = 0
            while (true) {
                emit(count++)
                delay(100.milliseconds)
            }
        }
        val compositionCount = AtomicInt(0)
        val onStartCount = AtomicInt(0)

        composeTestRule.setContent {
            val count by tickerFlow.collectAsState(0)

            compositionCount.fetchAndIncrement()

            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("https://example.com/image${count.coerceAtMost(1)}")
                    .listener(
                        onStart = {
                            onStartCount.fetchAndIncrement()
                        },
                    )
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                imageLoader = imageLoader,
            )

            BasicText("Count: $count")
        }

        waitUntil { compositionCount.load() >= 3 }

        assertEquals(2, onStartCount.load())
    }

    /** Regression test: https://github.com/coil-kt/coil/issues/2573 */
    @Test
    fun minIntrinsicSize() {
        assumeSupportsCaptureToImage()

        val dstWidth = 100.dp
        val dstHeight = 150.dp

        composeTestRule.setContent {
            Box(
                modifier = Modifier
                    .width(IntrinsicSize.Min)
                    .height(IntrinsicSize.Min)
                    .sizeIn(maxWidth = dstWidth, maxHeight = dstHeight),
            ) {
                AsyncImage(
                    model = "https://example.com/image",
                    contentDescription = null,
                    imageLoader = imageLoader,
                    modifier = Modifier
                        .testTag(Image),
                )
            }
        }

        waitForRequestComplete()

        val scale = DecodeUtils.computeSizeMultiplier(
            srcWidth = SampleWidth.toFloat(),
            srcHeight = SampleHeight.toFloat(),
            dstWidth = dstWidth.toPx(),
            dstHeight = dstHeight.toPx(),
            scale = Scale.FIT,
        ).coerceAtMost(1f)

        composeTestRule.onNodeWithTag(Image)
            .assertIsDisplayed()
            .assertWidthIsEqualTo((scale * SampleWidth).toDp())
            .assertHeightIsEqualTo((scale * SampleHeight).toDp())
            .captureToImage()
            .assertIsSimilarTo(R.drawable.sample)
    }

    /** Regression test: https://github.com/coil-kt/coil/issues/2573 */
    @Test
    fun maxIntrinsicSize() {
        assumeSupportsCaptureToImage()

        val dstWidth = 100.dp
        val dstHeight = 150.dp

        composeTestRule.setContent {
            Box(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .height(IntrinsicSize.Max)
                    .sizeIn(maxWidth = dstWidth, maxHeight = dstHeight),
            ) {
                AsyncImage(
                    model = "https://example.com/image",
                    contentDescription = null,
                    imageLoader = imageLoader,
                    modifier = Modifier
                        .testTag(Image),
                )
            }
        }

        waitForRequestComplete()

        val scale = DecodeUtils.computeSizeMultiplier(
            srcWidth = SampleWidth.toFloat(),
            srcHeight = SampleHeight.toFloat(),
            dstWidth = dstWidth.toPx(),
            dstHeight = dstHeight.toPx(),
            scale = Scale.FIT,
        ).coerceAtMost(1f)

        composeTestRule.onNodeWithTag(Image)
            .assertIsDisplayed()
            .assertWidthIsEqualTo((scale * SampleWidth).toDp())
            .assertHeightIsEqualTo((scale * SampleHeight).toDp())
            .captureToImage()
            .assertIsSimilarTo(R.drawable.sample)
    }

    /** Regression test: https://github.com/coil-kt/coil/issues/2573 */
    @Test
    fun mixedIntrinsicSize() {
        assumeSupportsCaptureToImage()

        val dstWidth = 100.dp
        val dstHeight = 150.dp

        composeTestRule.setContent {
            Box(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .height(IntrinsicSize.Min)
                    .sizeIn(maxWidth = dstWidth, maxHeight = dstHeight),
            ) {
                AsyncImage(
                    model = "https://example.com/image",
                    contentDescription = null,
                    imageLoader = imageLoader,
                    modifier = Modifier
                        .testTag(Image),
                )
            }
        }

        waitForRequestComplete()

        val scale = DecodeUtils.computeSizeMultiplier(
            srcWidth = SampleWidth.toFloat(),
            srcHeight = SampleHeight.toFloat(),
            dstWidth = dstWidth.toPx(),
            dstHeight = dstHeight.toPx(),
            scale = Scale.FIT,
        ).coerceAtMost(1f)

        composeTestRule.onNodeWithTag(Image)
            .assertIsDisplayed()
            .assertWidthIsEqualTo((scale * SampleWidth).toDp())
            .assertHeightIsEqualTo((scale * SampleHeight).toDp())
            .captureToImage()
            .assertIsSimilarTo(R.drawable.sample)
    }

    @Test
    fun recomposesOnlyWhenFlowEmits_before() {
        val key = Extras.Key(0)
        val value = AtomicInt(0)
        val totalCompositions = 10
        val compositionCount = AtomicInt(0)

        var flowEmissions = 0
        val flow = flow {
            while (flowEmissions < totalCompositions) {
                emit(flowEmissions)
                delay(20.milliseconds)
                flowEmissions++
            }
        }

        composeTestRule.setContent {
            val observableValue by flow.collectAsState(0)

            if (compositionCount.incrementAndFetch() > totalCompositions) {
                error("too many compositions")
            }

            // Need to observe the value.
            observableValue.toString()

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("https://example.com/image")
                    .apply { extras[key] = value.fetchAndIncrement() }
                    .build(),
                contentDescription = null,
                imageLoader = imageLoader,
            )
        }

        waitForRequestComplete()

        composeTestRule.waitUntil(10_000) {
            flowEmissions >= totalCompositions
        }

        assertEquals(totalCompositions, compositionCount.load())
        assertEquals(1, requestTracker.startedRequests)
        assertEquals(1, requestTracker.finishedRequests)
    }

    @Test
    fun recomposesOnlyWhenFlowEmits_after() {
        val key = Extras.Key(0)
        val value = AtomicInt(0)
        val totalCompositions = 10
        val compositionCount = AtomicInt(0)

        var flowEmissions = 0
        val flow = flow {
            while (flowEmissions < totalCompositions) {
                emit(flowEmissions)
                delay(20.milliseconds)
                flowEmissions++
            }
        }

        composeTestRule.setContent {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("https://example.com/image")
                    .apply { extras[key] = value.fetchAndIncrement() }
                    .build(),
                contentDescription = null,
                imageLoader = imageLoader,
            )

            val observableValue by flow.collectAsState(0)

            if (compositionCount.incrementAndFetch() > totalCompositions) {
                error("too many compositions")
            }

            // Need to observe the value.
            observableValue.toString()
        }

        waitForRequestComplete()

        composeTestRule.waitUntil(10_000) {
            flowEmissions >= totalCompositions
        }

        assertEquals(totalCompositions, compositionCount.load())
        assertEquals(1, requestTracker.startedRequests)
        assertEquals(1, requestTracker.finishedRequests)
    }

    @Test
    fun zeroHeightWhenAsyncImageIsEmpty() {
        composeTestRule.setContent {
            Column(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(Unit)
                        // Skip waiting for the constraints which will always be empty.
                        .size(100)
                        .build(),
                    contentDescription = null,
                    imageLoader = imageLoader,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(Image),
                )
            }
        }

        waitForRequestComplete()

        composeTestRule.onNodeWithTag(Image)
            .assertWidthIsEqualTo(context.resources.displayMetrics.widthPixels.toDp())
            .assertHeightIsEqualTo(0.dp)
    }

    /** Regression test: https://github.com/coil-kt/coil/issues/2693 */
    @Test
    fun recomposesWhenTransformationsChange() {
        val totalCompositions = 2
        val compositionCount = AtomicInt(0)

        val fakeTransformation = object : Transformation() {
            override val cacheKey: String
                get() = "cache_key"
            override suspend fun transform(input: Bitmap, size: Size) = input
        }

        val flow = MutableStateFlow(listOf(fakeTransformation))

        composeTestRule.setContent {
            if (compositionCount.incrementAndFetch() > totalCompositions) {
                error("too many compositions")
            }

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("https://example.com/image")
                    .transformations(flow.collectAsState().value)
                    .build(),
                contentDescription = null,
                imageLoader = imageLoader,
            )
        }

        waitForRequestComplete(finishedRequests = 1)

        flow.value = listOf(fakeTransformation, fakeTransformation)

        waitForRequestComplete(finishedRequests = 2)

        assertEquals(totalCompositions, compositionCount.load())
        assertEquals(2, requestTracker.startedRequests)
        assertEquals(2, requestTracker.finishedRequests)
    }

    private fun waitForRequestComplete(finishedRequests: Int = 1) = waitUntil {
        requestTracker.finishedRequests >= finishedRequests
    }

    private fun waitUntil(condition: () -> Boolean) {
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(10_000, condition)
        composeTestRule.waitForIdle()
    }

    private fun assertLoadedBitmapSize(width: Int, height: Int, requestNumber: Int = 0) {
        val result = assertIs<SuccessResult>(requestTracker.results[requestNumber])
        val bitmap = assertIs<BitmapImage>(result.image).bitmap
        assertContains((width - 1)..(width + 1), bitmap.width)
        assertContains((height - 1)..(height + 1), bitmap.height)
    }

    private fun assertSampleLoadedBitmapSize(
        composableWidth: Double,
        composableHeight: Double,
        scale: Scale = Scale.FIT,
        requestNumber: Int = 0,
    ) {
        val multiplier = DecodeUtils.computeSizeMultiplier(
            srcWidth = SampleWidth.toDouble(),
            srcHeight = SampleHeight.toDouble(),
            dstWidth = composableWidth,
            dstHeight = composableHeight,
            scale = scale,
        ).coerceAtMost(1.0)
        assertLoadedBitmapSize(
            width = (multiplier * SampleWidth).toInt(),
            height = (multiplier * SampleHeight).toInt(),
            requestNumber = requestNumber,
        )
    }

    private fun Dp.toPx() = with(composeTestRule.density) { toPx() }

    private fun Int.toDp() = with(composeTestRule.density) { toDp() }

    private fun Float.toDp() = with(composeTestRule.density) { toDp() }

    private fun Double.toDp() = with(composeTestRule.density) { toFloat().toDp() }

    private val displaySize: IntSize
        get() = composeTestRule.activity.findViewById<View>(android.R.id.content)!!
            .run { IntSize(width, height) }

    private class LoadingFetcher : Fetcher {

        override suspend fun fetch(): FetchResult? {
            while (true) delay(100)
        }

        class Factory : Fetcher.Factory<Any> {
            override fun create(
                data: Any,
                options: Options,
                imageLoader: ImageLoader,
            ) = LoadingFetcher()
        }
    }

    private class ErrorFetcher : Fetcher {

        override suspend fun fetch(): FetchResult? {
            throw IllegalStateException()
        }

        class Factory : Fetcher.Factory<Any> {
            override fun create(
                data: Any,
                options: Options,
                imageLoader: ImageLoader,
            ) = ErrorFetcher()
        }
    }

    companion object {
        private const val Image = "image"
        private const val Loading = "loading"
        private const val Error = "error"
        private const val Success = "success"
        private const val Content = "content"
        private const val SampleWidth = 1024
        private const val SampleHeight = 1326
    }
}

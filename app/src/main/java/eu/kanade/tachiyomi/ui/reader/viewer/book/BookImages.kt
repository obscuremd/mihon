package eu.kanade.tachiyomi.ui.reader.viewer.book

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import ca.mpreg.imagedecoder.ImageDecoder
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

/**
 * The page images of a book, loaded for the pages it's [keep]ing and decoded about as large as they're shown.
 * Their sizes stay known after the images are let go, so spreads keep their shape.
 */
internal class BookImages(private val scope: CoroutineScope) {

    val bitmaps = mutableStateMapOf<ReaderPage, ImageBitmap>()
    val sizes = mutableStateMapOf<ReaderPage, IntSize>()
    val errors = mutableStateMapOf<ReaderPage, Throwable>()

    private val jobs = HashMap<ReaderPage, Job>()

    /** Decoded pages, least recently wanted first, so the ones turned away from longest go first. */
    private val recent = LinkedHashSet<ReaderPage>()

    /** Big pages decode a couple at a time, so turning fast doesn't pile them up in memory. */
    private val decodes = Semaphore(PARALLEL_DECODES)

    /** The viewer's size, which no page is decoded much larger than. */
    var viewport = IntSize(1080, 1920)

    /**
     * Width over height of most pages seen so far, for pages whose size isn't known yet, so they don't change shape
     * when they load.
     */
    val typicalAspect: Float by derivedStateOf {
        val aspects = sizes.values
            .filter { it.width > 0 && it.height > 0 }
            .map { it.width.toFloat() / it.height }
            .filter { it < 1f }
            .sorted()
        aspects.getOrNull(aspects.size / 2) ?: DEFAULT_ASPECT
    }

    /**
     * Loads [pages]. Others stop loading, and the decoded ones turned away from longest are let go once there are
     * more than a few, so turning back shows them at once.
     */
    fun keep(pages: Set<ReaderPage>) {
        val iterator = jobs.entries.iterator()
        while (iterator.hasNext()) {
            val (page, job) = iterator.next()
            if (page !in pages) {
                job.cancel()
                iterator.remove()
                errors.remove(page)
            }
        }
        pages.forEach {
            recent.remove(it)
            recent.add(it)
        }
        while (recent.size > MAX_DECODED) {
            val oldest = recent.firstOrNull { it !in pages } ?: break
            recent.remove(oldest)
            bitmaps.remove(oldest)
        }
        for (page in pages) {
            if (page !in jobs) jobs[page] = scope.launch { load(page) }
        }
    }

    fun clear() {
        jobs.values.forEach(Job::cancel)
        jobs.clear()
        recent.clear()
        bitmaps.clear()
        errors.clear()
    }

    /**
     * Asks the page's loader for it and decodes it each time it's ready, as after a retry. Runs until cancelled.
     */
    private suspend fun load(page: ReaderPage) {
        val loader = page.chapter.pageLoader ?: return
        supervisorScope {
            launch(Dispatchers.IO) { loader.loadPage(page) }
            page.statusFlow.collectLatest { state ->
                // Kept from before: nothing to decode again
                if (state == Page.State.Ready && page !in bitmaps) decode(page)
            }
        }
    }

    private suspend fun decode(page: ReaderPage) {
        val stream = page.stream ?: return
        val viewport = viewport
        try {
            val (bitmap, size) = decodes.withPermit {
                withContext(Dispatchers.IO) { decodePage(stream, viewport) }
            }
            bitmap.prepareToDraw()
            sizes[page] = size
            bitmaps[page] = bitmap.asImageBitmap()
            errors.remove(page)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
            errors[page] = e
        }
    }
}

/**
 * Decodes the image from [open] no larger than it is when fit in [viewport], with its full size.
 */
private fun decodePage(open: () -> InputStream, viewport: IntSize): Pair<Bitmap, IntSize> {
    val bytes = open().use { it.readBytes() }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth > 0 && bounds.outHeight > 0) {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, viewport)
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let {
            return it to IntSize(bounds.outWidth, bounds.outHeight)
        }
    }
    // Formats the system decoder doesn't know, like JPEG XL
    return ImageDecoder.open(bytes.inputStream()).use { decoder ->
        val sample = sampleSize(decoder.width, decoder.height, viewport)
        decoder.decodeToBitmap(null, sample) to IntSize(decoder.width, decoder.height)
    }
}

/**
 * The largest power of two to shrink a [width] by [height] image by that keeps it at least as large as it shows fit
 * in [viewport], and within what the GPU can draw.
 */
private fun sampleSize(width: Int, height: Int, viewport: IntSize): Int {
    val scale = min(viewport.width.toFloat() / width, viewport.height.toFloat() / height).coerceAtMost(1f)
    val shownWidth = width * scale
    var sample = 1
    while (width / (sample * 2f) >= shownWidth) sample *= 2
    while (max(width, height) / sample > MAX_BITMAP_SIDE) sample *= 2
    return sample
}

private const val MAX_BITMAP_SIDE = 4096

/** Width over height of a page before any page's size is known. */
private const val DEFAULT_ASPECT = 0.7f

/** Decoded pages kept at most, including the ones loading ahead. */
private const val MAX_DECODED = 16

private const val PARALLEL_DECODES = 2

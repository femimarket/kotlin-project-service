package market.femi.demo

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import market.femi.ProjectService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Instrumented port of the Swift `ProjectServiceTests` suite. Runs on a real
 * device/emulator and exercises the library exactly as a downstream consumer
 * would — through the public [ProjectService] API only.
 *
 * Run with: `./gradlew :demo:connectedAndroidTest`
 */
@RunWith(AndroidJUnit4::class)
class ProjectServiceInstrumentedTest {

    private val prefix = "psvc-test-${UUID.randomUUID()}-"
    private fun name(stem: String) = "$prefix$stem"

    @Before
    fun setUp() {
        ProjectService.init(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    // MARK: - documents

    @Test
    fun documentsIsAReadableDirectory() {
        val dir = ProjectService.documents
        assertTrue(dir.exists() && dir.isDirectory)
    }

    // MARK: - saveFile

    @Test
    fun saveFileWithoutMetadataWritesBytesUnchanged() {
        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val file = name("plain.bin")
        ProjectService.saveFile(bytes, file)
        assertArrayEquals(bytes, ProjectService.getUrl(file).readBytes())
    }

    @Test
    fun saveFileEmbedsPrompt() {
        val file = name("prompt.png")
        ProjectService.saveFile(makePNG(), file, prompt = "hello world")
        assertEquals("hello world", ProjectService.getPrompt(file))
    }

    @Test
    fun saveFileEmbedsModel() {
        val file = name("model.png")
        ProjectService.saveFile(makePNG(), file, model = "dalle-3")
        assertEquals("dalle-3", ProjectService.getModel(file))
    }

    @Test
    fun saveFileEmbedsBoth() {
        val file = name("both.png")
        ProjectService.saveFile(makePNG(), file, prompt = "p", model = "m")
        assertEquals("p", ProjectService.getPrompt(file))
        assertEquals("m", ProjectService.getModel(file))
    }

    // Public getPrompt reads Iptc4xmpExt:AIPromptInformation first, so this
    // asserts that field is what was written (the Swift raw-namespace check).
    @Test
    fun saveFileWritesIptcExtAIPromptInformation() {
        val file = name("iptcext-prompt.png")
        ProjectService.saveFile(makePNG(), file, prompt = "what is AI")
        assertEquals("what is AI", ProjectService.getPrompt(file))
    }

    @Test
    fun saveFileWritesIptcExtAISystemUsed() {
        val file = name("iptcext-model.png")
        ProjectService.saveFile(makePNG(), file, model = "dalle-3")
        assertEquals("dalle-3", ProjectService.getModel(file))
    }

    @Test
    fun saveFileEmbedsSubject() {
        val file = name("subject.png")
        ProjectService.saveFile(makePNG(), file, subject = listOf("cat", "fluffy", "studio"))
        assertEquals(listOf("cat", "fluffy", "studio"), ProjectService.getSubject(file))
    }

    @Test
    fun saveFileEmbedsAllThree() {
        val file = name("all.png")
        ProjectService.saveFile(makePNG(), file, prompt = "p", model = "m", subject = listOf("a", "b"))
        assertEquals("p", ProjectService.getPrompt(file))
        assertEquals("m", ProjectService.getModel(file))
        assertEquals(listOf("a", "b"), ProjectService.getSubject(file))
    }

    @Test
    fun getSubjectNilWhenAbsent() {
        val file = name("no-subject.png")
        ProjectService.saveFile(makePNG(), file)
        assertNull(ProjectService.getSubject(file))
    }

    @Test
    fun getSubjectNilWhenEmptyArrayPassed() {
        val file = name("empty-subject.png")
        ProjectService.saveFile(makePNG(), file, subject = emptyList())
        assertNull(ProjectService.getSubject(file))
    }

    // MARK: - saveFile (video, MP4 uuid box)

    @Test
    fun saveFileWithoutMetadataWritesVideoBytesUnchanged() {
        val video = makeMP4()
        val file = name("plain.mp4")
        ProjectService.saveFile(video, file)
        assertArrayEquals(video, ProjectService.getUrl(file).readBytes())
    }

    @Test
    fun saveFileEmbedsPromptInVideo() {
        val file = name("video-prompt.mp4")
        ProjectService.saveFile(makeMP4(), file, prompt = "a video of a fox")
        assertEquals("a video of a fox", ProjectService.getPrompt(file))
    }

    @Test
    fun saveFileEmbedsModelInVideo() {
        val file = name("video-model.mp4")
        ProjectService.saveFile(makeMP4(), file, model = "sora-1")
        assertEquals("sora-1", ProjectService.getModel(file))
    }

    @Test
    fun saveFileEmbedsSubjectInVideo() {
        val file = name("video-subject.mp4")
        ProjectService.saveFile(makeMP4(), file, subject = listOf("fox", "wildlife"))
        assertEquals(listOf("fox", "wildlife"), ProjectService.getSubject(file))
    }

    @Test
    fun saveFileEmbedsAllThreeInVideo() {
        val file = name("video-all.mp4")
        ProjectService.saveFile(makeMP4(), file, prompt = "p", model = "m", subject = listOf("a", "b"))
        assertEquals("p", ProjectService.getPrompt(file))
        assertEquals("m", ProjectService.getModel(file))
        assertEquals(listOf("a", "b"), ProjectService.getSubject(file))
    }

    @Test
    fun saveFileWritesIptcExtAIPromptInformationInVideo() {
        val file = name("video-iptcext-prompt.mp4")
        ProjectService.saveFile(makeMP4(), file, prompt = "what is AI video")
        assertEquals("what is AI video", ProjectService.getPrompt(file))
    }

    @Test
    fun saveFileWritesIptcExtAISystemUsedInVideo() {
        val file = name("video-iptcext-model.mp4")
        ProjectService.saveFile(makeMP4(), file, model = "sora-1")
        assertEquals("sora-1", ProjectService.getModel(file))
    }

    @Test
    fun saveFileOverwritesExisting() {
        val file = name("overwrite.png")
        ProjectService.saveFile(makePNG(), file, prompt = "first")
        ProjectService.saveFile(makePNG(), file, prompt = "second")
        assertEquals("second", ProjectService.getPrompt(file))
    }

    @Test
    fun getPromptNilWhenAbsent() {
        val file = name("no-prompt.png")
        ProjectService.saveFile(makePNG(), file)
        assertNull(ProjectService.getPrompt(file))
    }

    @Test
    fun getModelNilWhenAbsent() {
        val file = name("no-model.png")
        ProjectService.saveFile(makePNG(), file)
        assertNull(ProjectService.getModel(file))
    }

    // MARK: - like

    @Test
    fun likeTrueThenRead() {
        val file = name("like.png")
        ProjectService.saveFile(makePNG(), file)
        ProjectService.like(file, true)
        assertTrue(ProjectService.getLike(file))
    }

    @Test
    fun likeFalseAfterTrue() {
        val file = name("unlike.png")
        ProjectService.saveFile(makePNG(), file)
        ProjectService.like(file, true)
        ProjectService.like(file, false)
        assertFalse(ProjectService.getLike(file))
    }

    @Test
    fun getLikeFalseWhenAbsent() {
        val file = name("never-liked.png")
        ProjectService.saveFile(makePNG(), file)
        assertFalse(ProjectService.getLike(file))
    }

    // MARK: - getAllGenerations

    @Test
    fun getAllGenerationsIncludesSaved() {
        val file = name("listed.png")
        ProjectService.saveFile(makePNG(), file)
        assertTrue(ProjectService.getAllGenerations().any { it.name == file })
    }

    // MARK: - saveAudio / getAudio

    @Test
    fun saveAudioWritesAndGetAudioReturnsIt() {
        val file = name("audio.m4a")
        ProjectService.saveAudio(byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()), file)
        assertEquals(file, ProjectService.getAudio()?.name)
    }

    @Test
    fun saveAudioDeletesPriorAudio() {
        val first = name("first.mp3")
        val second = name("second.wav")
        ProjectService.saveAudio(byteArrayOf(0x01), first)
        ProjectService.saveAudio(byteArrayOf(0x02), second)
        assertFalse(ProjectService.getUrl(first).exists())
        assertTrue(ProjectService.getUrl(second).exists())
    }

    @Test
    fun getAudioNilWhenNoAudioPresent() {
        val audioExt = setOf("mp3", "m4a", "wav", "aac", "caf", "aiff", "aif", "flac", "ogg", "opus")
        ProjectService.getAllGenerations()
            .filter { it.extension.lowercase() in audioExt }
            .forEach { it.delete() }
        assertNull(ProjectService.getAudio())
    }

    @Test
    fun saveAudioDoesNotDeleteImages() {
        val img = name("keep.png")
        val aud = name("audio.mp3")
        ProjectService.saveFile(makePNG(), img, prompt = "x")
        ProjectService.saveAudio(byteArrayOf(0x01), aud)
        assertTrue(ProjectService.getUrl(img).exists())
        assertEquals("x", ProjectService.getPrompt(img))
    }

    // MARK: - characterCast (in-memory)

    @Test
    fun setCharacterCastRoundTripsViaGet() {
        ProjectService.setCharacterCast("hero.png", "villain.png")
        val pair = ProjectService.getCharacterCast()
        assertEquals("hero.png", pair?.first)
        assertEquals("villain.png", pair?.second)
    }

    @Test
    fun setCharacterCastSecondCallOverwrites() {
        ProjectService.setCharacterCast("a.png", "b.png")
        ProjectService.setCharacterCast("c.png", "d.png")
        val pair = ProjectService.getCharacterCast()
        assertEquals("c.png", pair?.first)
        assertEquals("d.png", pair?.second)
    }

    @Test
    fun clearCharacterCastAfterSetReturnsNil() {
        ProjectService.setCharacterCast("hero.png", "villain.png")
        ProjectService.clearCharacterCast()
        assertNull(ProjectService.getCharacterCast())
    }

    @Test
    fun clearCharacterCastWhenEmptyIsNoOp() {
        ProjectService.clearCharacterCast()
        ProjectService.clearCharacterCast()
        assertNull(ProjectService.getCharacterCast())
    }

    // MARK: - imageEdit (in-memory)

    @Test
    fun setImageEditRoundTripsViaGet() {
        ProjectService.setImageEdit("portrait.png")
        assertEquals("portrait.png", ProjectService.getImageEdit())
    }

    @Test
    fun setImageEditSecondCallOverwrites() {
        ProjectService.setImageEdit("a.png")
        ProjectService.setImageEdit("b.png")
        assertEquals("b.png", ProjectService.getImageEdit())
    }

    @Test
    fun clearImageEditAfterSetReturnsNil() {
        ProjectService.setImageEdit("portrait.png")
        ProjectService.clearImageEdit()
        assertNull(ProjectService.getImageEdit())
    }

    @Test
    fun clearImageEditWhenEmptyIsNoOp() {
        ProjectService.clearImageEdit()
        ProjectService.clearImageEdit()
        assertNull(ProjectService.getImageEdit())
    }

    // MARK: - getUrl

    @Test
    fun getUrlPointsIntoDocuments() {
        val url = ProjectService.getUrl("anything.png")
        assertEquals(ProjectService.documents.canonicalFile, url.parentFile?.canonicalFile)
        assertEquals("anything.png", url.name)
    }

    @Test
    fun getUrlStripsPathTraversal() {
        val url = ProjectService.getUrl("../../../etc/passwd")
        assertEquals("passwd", url.name)
        assertEquals(ProjectService.documents.canonicalFile, url.parentFile?.canonicalFile)
    }

    // MARK: - helpers

    private fun assertArrayEquals(expected: ByteArray, actual: ByteArray) =
        org.junit.Assert.assertArrayEquals(expected, actual)

    private fun makePNG(): ByteArray {
        val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bmp.setPixel(0, 0, Color.WHITE)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    /** A minimal but structurally valid MP4 (ftyp + mdat) for uuid-box muxing. */
    private fun makeMP4(): ByteArray {
        val out = ByteArrayOutputStream()
        fun box(type: String, payload: ByteArray) {
            val size = 8 + payload.size
            out.write(byteArrayOf((size ushr 24).toByte(), (size ushr 16).toByte(), (size ushr 8).toByte(), size.toByte()))
            out.write(type.toByteArray(Charsets.US_ASCII))
            out.write(payload)
        }
        val ftyp = ByteArrayOutputStream().apply {
            write("isom".toByteArray(Charsets.US_ASCII)) // major brand
            write(byteArrayOf(0, 0, 2, 0))               // minor version
            write("isom".toByteArray(Charsets.US_ASCII)) // compatible brands
            write("mp41".toByteArray(Charsets.US_ASCII))
        }.toByteArray()
        box("ftyp", ftyp)
        box("mdat", ByteArray(16))
        return out.toByteArray()
    }
}

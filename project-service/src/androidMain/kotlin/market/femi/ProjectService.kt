package market.femi

import android.annotation.SuppressLint
import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Local file storage boundary for Android — the port of the Swift
 * `ProjectService`. Every save and read goes through here; nothing in this
 * object touches the network. XMP metadata is embedded/read via [XmpMetadata]
 * (Adobe XMP Core + per-container muxing), mirroring the Rust `xmp-toolkit`
 * shim used on iOS.
 *
 * The iOS `Documents/` directory maps to the app's private [Context.getFilesDir].
 * Call [init] once (e.g. from `Application.onCreate`) before any other method.
 */
object ProjectService {

    @SuppressLint("StaticFieldLeak") // application context only — no leak
    @Volatile
    private var appContext: Context? = null

    /** Bind the application context. Idempotent. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** App-private storage root — the analog of iOS `Documents/`. */
    val documents: File
        get() = (appContext
            ?: error("ProjectService.init(context) must be called before use")).filesDir

    /**
     * Embed XMP metadata into [data] and write to `documents/<file>`.
     *
     * - prompt → `dc:description` (Lang Alt) and `Iptc4xmpExt:AIPromptInformation`
     * - model  → `xmp:CreatorTool` and `Iptc4xmpExt:AISystemUsed`
     * - subject → `dc:subject` (Bag)
     *
     * When all three are null the input bytes are written through unchanged.
     */
    fun saveFile(
        data: ByteArray,
        named: String,
        prompt: String? = null,
        model: String? = null,
        subject: List<String>? = null,
    ) {
        val bytes = if (prompt == null && model == null && subject == null) {
            data
        } else {
            XmpMetadata.embed(data, extensionOf(named), prompt, model, subject)
        }
        writeBytesToDocuments(bytes, named)
    }

    /** Set the like state by writing `xmp:Rating` (5 = liked, 0 = not). */
    fun like(file: String, liked: Boolean) {
        val dest = getUrl(file)
        val current = dest.takeIf { it.exists() }?.readBytes() ?: return
        val updated = XmpMetadata.setRating(current, extensionOf(file), if (liked) 5 else 0)
        dest.writeBytes(updated)
    }

    /** List every file in the app's storage folder. */
    fun getAllGenerations(): List<File> =
        documents.listFiles()?.toList() ?: emptyList()

    /**
     * Replace the audio file in storage. Any existing audio files are deleted
     * first, then [data] is written as [named].
     */
    fun saveAudio(data: ByteArray, named: String) {
        getAllGenerations().filter(::isAudio).forEach { it.delete() }
        writeBytesToDocuments(data, named)
    }

    /** The lone audio file in storage, if any. */
    fun getAudio(): File? = getAllGenerations().firstOrNull(::isAudio)

    /**
     * Read the prompt from `Iptc4xmpExt:AIPromptInformation` (falling back to
     * `dc:description[x-default]`). Null when absent.
     */
    fun getPrompt(file: String): String? =
        readBytes(file)?.let { XmpMetadata.readPrompt(it) }

    /**
     * Read the model from `Iptc4xmpExt:AISystemUsed` (falling back to
     * `xmp:CreatorTool`). Null when absent.
     */
    fun getModel(file: String): String? =
        readBytes(file)?.let { XmpMetadata.readModel(it) }

    /** Read the subject keywords from `dc:subject`. Null when absent. */
    fun getSubject(file: String): List<String>? =
        readBytes(file)?.let { XmpMetadata.readSubject(it) }

    /** Read the like state from `xmp:Rating` (`1..5` = liked). */
    fun getLike(file: String): Boolean {
        val rating = readBytes(file)?.let { XmpMetadata.readRating(it) } ?: return false
        return rating in 1..5
    }

    /** Resolve [file] to a path inside [documents], stripping any traversal. */
    fun getUrl(file: String): File = File(documents, File(file).name)

    // MARK: - Operation arguments (in-memory, process-lifetime)

    @Volatile
    private var characterCast: Pair<String, String>? = null

    /** Store the two filenames for the character-cast operation. */
    fun setCharacterCast(a: String, b: String) {
        characterCast = a to b
    }

    /** The previously-set character-cast pair, or null. */
    fun getCharacterCast(): Pair<String, String>? = characterCast

    /** Drop the stored character-cast pair. Idempotent. */
    fun clearCharacterCast() {
        characterCast = null
    }

    @Volatile
    private var imageEdit: String? = null

    /** Store the filename for the image-edit operation. */
    fun setImageEdit(file: String) {
        imageEdit = file
    }

    /** The previously-set image-edit filename, or null. */
    fun getImageEdit(): String? = imageEdit

    /** Drop the stored image-edit filename. Idempotent. */
    fun clearImageEdit() {
        imageEdit = null
    }

    // MARK: - Internals

    private fun readBytes(file: String): ByteArray? =
        getUrl(file).takeIf { it.exists() }?.readBytes()

    private fun writeBytesToDocuments(data: ByteArray, file: String) {
        val dest = getUrl(file)
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".tmp-" + UUID.randomUUID())
        tmp.writeBytes(data)
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
        check(dest.exists()) { "saveFile: file not present after move at ${dest.path}" }
    }

    private val audioExtensions = setOf(
        "mp3", "m4a", "wav", "aac", "caf", "aiff", "aif", "flac", "ogg", "opus"
    )

    private fun isAudio(file: File): Boolean =
        file.extension.lowercase() in audioExtensions

    private fun extensionOf(file: String): String = File(file).extension.lowercase()
}

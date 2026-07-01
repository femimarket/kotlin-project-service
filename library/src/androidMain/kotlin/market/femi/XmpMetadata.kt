package market.femi

import com.adobe.internal.xmp.XMPConst
import com.adobe.internal.xmp.XMPMeta
import com.adobe.internal.xmp.XMPMetaFactory
import com.adobe.internal.xmp.options.PropertyOptions
import com.adobe.internal.xmp.options.SerializeOptions
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

/**
 * XMP embed/read for Android, ported from the Rust `psxmp_*` shim
 * (`xmp-toolkit`). Builds/parses the XMP packet with Adobe XMP Core and muxes
 * it into the container the same way the Adobe smart handler does:
 *
 * - PNG  → `iTXt` chunk, keyword `XML:com.adobe.xmp`
 * - JPEG → `APP1` segment, header `http://ns.adobe.com/xap/1.0/ `
 * - MP4  → top-level `uuid` box, UUID `BE7ACFCB97A942E89C71999491E3AFAC`
 *
 * Field mapping (identical to the Rust layer):
 * - prompt → `dc:description[x-default]` + `Iptc4xmpExt:AIPromptInformation`
 * - model  → `xmp:CreatorTool` + `Iptc4xmpExt:AISystemUsed`
 * - subject → `dc:subject` (Bag)
 * - rating → `xmp:Rating`
 */
internal object XmpMetadata {

    private const val IPTC_EXT_NS = "http://iptc.org/std/Iptc4xmpExt/2008-02-29/"

    init {
        // Match the Rust const namespace/prefix registration.
        runCatching {
            XMPMetaFactory.getSchemaRegistry().registerNamespace(IPTC_EXT_NS, "Iptc4xmpExt")
        }
    }

    private enum class Format { PNG, JPEG, MP4 }

    // MARK: - Public embed / read

    /** Embed prompt/model/subject into [data]. Unknown formats pass through. */
    fun embed(
        data: ByteArray,
        ext: String,
        prompt: String?,
        model: String?,
        subject: List<String>?,
    ): ByteArray {
        val format = formatFor(ext) ?: return data
        val meta = metaFrom(extractPacket(data, format))

        prompt?.let {
            meta.setLocalizedText(XMPConst.NS_DC, "description", null, "x-default", it)
            meta.setProperty(IPTC_EXT_NS, "AIPromptInformation", it)
        }
        model?.let {
            meta.setProperty(XMPConst.NS_XMP, "CreatorTool", it)
            meta.setProperty(IPTC_EXT_NS, "AISystemUsed", it)
        }
        if (!subject.isNullOrEmpty()) {
            if (meta.doesPropertyExist(XMPConst.NS_DC, "subject")) {
                meta.deleteProperty(XMPConst.NS_DC, "subject")
            }
            val arrayOpts = PropertyOptions().setArray(true)
            for (item in subject) {
                meta.appendArrayItem(XMPConst.NS_DC, "subject", arrayOpts, item, null)
            }
        }

        return injectPacket(data, format, serialize(meta))
    }

    /** Set `xmp:Rating`. Unknown formats pass through. */
    fun setRating(data: ByteArray, ext: String, rating: Int): ByteArray {
        val format = formatFor(ext) ?: return data
        val meta = metaFrom(extractPacket(data, format))
        meta.setPropertyInteger(XMPConst.NS_XMP, "Rating", rating)
        return injectPacket(data, format, serialize(meta))
    }

    fun readPrompt(data: ByteArray): String? {
        val meta = parse(data) ?: return null
        meta.getPropertyString(IPTC_EXT_NS, "AIPromptInformation")?.let { return it }
        return meta.getLocalizedText(XMPConst.NS_DC, "description", null, "x-default")?.value
    }

    fun readModel(data: ByteArray): String? {
        val meta = parse(data) ?: return null
        meta.getPropertyString(IPTC_EXT_NS, "AISystemUsed")?.let { return it }
        return meta.getPropertyString(XMPConst.NS_XMP, "CreatorTool")
    }

    fun readSubject(data: ByteArray): List<String>? {
        val meta = parse(data) ?: return null
        val count = meta.countArrayItems(XMPConst.NS_DC, "subject")
        if (count <= 0) return null
        val items = (1..count).mapNotNull { meta.getArrayItem(XMPConst.NS_DC, "subject", it)?.value }
        return items.ifEmpty { null }
    }

    /** Read `xmp:Rating`, or null when absent. */
    fun readRating(data: ByteArray): Int? {
        val meta = parse(data) ?: return null
        if (!meta.doesPropertyExist(XMPConst.NS_XMP, "Rating")) return null
        return meta.getPropertyInteger(XMPConst.NS_XMP, "Rating")
    }

    // MARK: - XMP packet helpers

    private fun metaFrom(packet: ByteArray?): XMPMeta =
        packet?.let { runCatching { XMPMetaFactory.parseFromBuffer(it) }.getOrNull() }
            ?: XMPMetaFactory.create()

    private fun parse(data: ByteArray): XMPMeta? {
        val format = formatForMagic(data) ?: return null
        val packet = extractPacket(data, format) ?: return null
        return runCatching { XMPMetaFactory.parseFromBuffer(packet) }.getOrNull()
    }

    private fun serialize(meta: XMPMeta): ByteArray {
        val opts = SerializeOptions().setUseCompactFormat(true).setUseCanonicalFormat(false)
        return XMPMetaFactory.serializeToBuffer(meta, opts)
    }

    // MARK: - Format detection

    private fun formatFor(ext: String): Format? = when (ext.lowercase()) {
        "png" -> Format.PNG
        "jpg", "jpeg", "jpe" -> Format.JPEG
        "mp4", "m4v", "mov" -> Format.MP4
        else -> null
    }

    private fun formatForMagic(d: ByteArray): Format? = when {
        d.size >= 8 && d[0] == 0x89.toByte() && d[1] == 'P'.code.toByte() &&
            d[2] == 'N'.code.toByte() && d[3] == 'G'.code.toByte() -> Format.PNG
        d.size >= 2 && d[0] == 0xFF.toByte() && d[1] == 0xD8.toByte() -> Format.JPEG
        d.size >= 8 && d[4] == 'f'.code.toByte() && d[5] == 't'.code.toByte() &&
            d[6] == 'y'.code.toByte() && d[7] == 'p'.code.toByte() -> Format.MP4
        else -> null
    }

    private fun extractPacket(data: ByteArray, format: Format): ByteArray? = when (format) {
        Format.PNG -> pngExtract(data)
        Format.JPEG -> jpegExtract(data)
        Format.MP4 -> mp4Extract(data)
    }

    private fun injectPacket(data: ByteArray, format: Format, packet: ByteArray): ByteArray = when (format) {
        Format.PNG -> pngInject(data, packet)
        Format.JPEG -> jpegInject(data, packet)
        Format.MP4 -> mp4Inject(data, packet)
    }

    // MARK: - PNG (iTXt "XML:com.adobe.xmp")

    private val PNG_SIG = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    private const val PNG_XMP_KEYWORD = "XML:com.adobe.xmp"

    private fun pngExtract(data: ByteArray): ByteArray? {
        var p = PNG_SIG.size
        val kw = PNG_XMP_KEYWORD.toByteArray(Charsets.ISO_8859_1)
        while (p + 8 <= data.size) {
            val len = readInt32BE(data, p)
            val type = String(data, p + 4, 4, Charsets.ISO_8859_1)
            val dataStart = p + 8
            if (type == "iTXt" && dataStart + kw.size + 1 <= data.size &&
                data.regionMatches(dataStart, kw) && data[dataStart + kw.size].toInt() == 0
            ) {
                // keyword \0 compFlag compMethod langTag \0 transKw \0 text
                var q = dataStart + kw.size + 1
                q += 2 // compression flag + method
                q = skipToNull(data, q) + 1 // language tag
                q = skipToNull(data, q) + 1 // translated keyword
                val textEnd = dataStart + len
                if (q <= textEnd) return data.copyOfRange(q, textEnd)
            }
            p = dataStart + len + 4 // + CRC
        }
        return null
    }

    private fun pngInject(data: ByteArray, packet: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(PNG_SIG)
        val kw = PNG_XMP_KEYWORD.toByteArray(Charsets.ISO_8859_1)
        var p = PNG_SIG.size
        while (p + 8 <= data.size) {
            val len = readInt32BE(data, p)
            val type = String(data, p + 4, 4, Charsets.ISO_8859_1)
            val chunkEnd = p + 8 + len + 4
            val dataStart = p + 8
            val isXmp = type == "iTXt" && dataStart + kw.size + 1 <= data.size &&
                data.regionMatches(dataStart, kw)
            if (type == "IEND") {
                writePngChunk(out, "iTXt", buildItxt(kw, packet))
                out.write(data, p, chunkEnd - p) // IEND
            } else if (!isXmp) {
                out.write(data, p, chunkEnd - p)
            }
            p = chunkEnd
        }
        return out.toByteArray()
    }

    private fun buildItxt(keyword: ByteArray, text: ByteArray): ByteArray {
        val b = ByteArrayOutputStream()
        b.write(keyword)
        b.write(0)          // null separator
        b.write(0)          // compression flag (uncompressed)
        b.write(0)          // compression method
        b.write(0)          // language tag (empty) + separator
        b.write(0)          // translated keyword (empty) + separator
        b.write(text)
        return b.toByteArray()
    }

    private fun writePngChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        out.write(int32BE(data.size))
        val typeBytes = type.toByteArray(Charsets.ISO_8859_1)
        out.write(typeBytes)
        out.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        out.write(int32BE(crc.value.toInt()))
    }

    // MARK: - JPEG (APP1 XMP)

    private const val JPEG_XMP_HEADER = "http://ns.adobe.com/xap/1.0/ "

    private fun jpegExtract(data: ByteArray): ByteArray? {
        val header = JPEG_XMP_HEADER.toByteArray(Charsets.ISO_8859_1)
        var p = 2 // skip SOI
        while (p + 4 <= data.size && data[p] == 0xFF.toByte()) {
            val marker = data[p + 1].toInt() and 0xFF
            if (marker == 0xDA || marker == 0xD9) break // SOS / EOI
            val segLen = ((data[p + 2].toInt() and 0xFF) shl 8) or (data[p + 3].toInt() and 0xFF)
            val segDataStart = p + 4
            if (marker == 0xE1 && segDataStart + header.size <= data.size &&
                data.regionMatches(segDataStart, header)
            ) {
                return data.copyOfRange(segDataStart + header.size, p + 2 + segLen)
            }
            p += 2 + segLen
        }
        return null
    }

    private fun jpegInject(data: ByteArray, packet: ByteArray): ByteArray {
        val header = JPEG_XMP_HEADER.toByteArray(Charsets.ISO_8859_1)
        val out = ByteArrayOutputStream()
        out.write(data, 0, 2) // SOI

        // New APP1 XMP segment right after SOI.
        val payloadLen = header.size + packet.size + 2
        require(payloadLen <= 0xFFFF) { "XMP packet too large for a single JPEG APP1 segment" }
        out.write(0xFF)
        out.write(0xE1)
        out.write(payloadLen ushr 8)
        out.write(payloadLen and 0xFF)
        out.write(header)
        out.write(packet)

        // Copy remaining segments, dropping any existing XMP APP1.
        var p = 2
        while (p + 4 <= data.size && data[p] == 0xFF.toByte()) {
            val marker = data[p + 1].toInt() and 0xFF
            if (marker == 0xDA) { // SOS: copy the rest verbatim
                out.write(data, p, data.size - p)
                return out.toByteArray()
            }
            val segLen = ((data[p + 2].toInt() and 0xFF) shl 8) or (data[p + 3].toInt() and 0xFF)
            val segEnd = p + 2 + segLen
            val isXmp = marker == 0xE1 && p + 4 + header.size <= data.size &&
                data.regionMatches(p + 4, header)
            if (!isXmp) out.write(data, p, segEnd - p)
            p = segEnd
        }
        if (p < data.size) out.write(data, p, data.size - p)
        return out.toByteArray()
    }

    // MARK: - MP4 (top-level uuid box)

    private val MP4_XMP_UUID = byteArrayOf(
        0xBE.toByte(), 0x7A.toByte(), 0xCF.toByte(), 0xCB.toByte(),
        0x97.toByte(), 0xA9.toByte(), 0x42.toByte(), 0xE8.toByte(),
        0x9C.toByte(), 0x71.toByte(), 0x99.toByte(), 0x94.toByte(),
        0x91.toByte(), 0xE3.toByte(), 0xAF.toByte(), 0xAC.toByte(),
    )

    private fun mp4Boxes(data: ByteArray, visit: (type: String, start: Int, end: Int, payload: Int) -> Unit) {
        var p = 0
        while (p + 8 <= data.size) {
            val size32 = readInt32BE(data, p).toLong() and 0xFFFFFFFFL
            val type = String(data, p + 4, 4, Charsets.ISO_8859_1)
            var payload = p + 8
            val end: Int = when (size32) {
                1L -> { // 64-bit largesize
                    val big = readInt64BE(data, p + 8)
                    payload = p + 16
                    (p + big).toInt()
                }
                0L -> data.size // extends to end
                else -> (p + size32).toInt()
            }
            if (end <= p || end > data.size) return
            visit(type, p, end, payload)
            p = end
        }
    }

    private fun mp4Extract(data: ByteArray): ByteArray? {
        var result: ByteArray? = null
        mp4Boxes(data) { type, _, end, payload ->
            if (type == "uuid" && payload + 16 <= end && data.regionMatches(payload, MP4_XMP_UUID)) {
                result = data.copyOfRange(payload + 16, end)
            }
        }
        return result
    }

    private fun mp4Inject(data: ByteArray, packet: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        // Copy every top-level box except an existing XMP uuid box.
        mp4Boxes(data) { type, start, end, payload ->
            val isXmp = type == "uuid" && payload + 16 <= end &&
                data.regionMatches(payload, MP4_XMP_UUID)
            if (!isXmp) out.write(data, start, end - start)
        }
        // Append a fresh uuid box.
        val boxSize = 8 + 16 + packet.size
        out.write(int32BE(boxSize))
        out.write("uuid".toByteArray(Charsets.ISO_8859_1))
        out.write(MP4_XMP_UUID)
        out.write(packet)
        return out.toByteArray()
    }

    // MARK: - byte utils

    private fun readInt32BE(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or (b[off + 3].toInt() and 0xFF)

    private fun readInt64BE(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (b[off + i].toLong() and 0xFF)
        return v
    }

    private fun int32BE(v: Int): ByteArray = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte()
    )

    private fun skipToNull(b: ByteArray, from: Int): Int {
        var i = from
        while (i < b.size && b[i].toInt() != 0) i++
        return i
    }

    private fun ByteArray.regionMatches(offset: Int, other: ByteArray): Boolean {
        if (offset < 0 || offset + other.size > size) return false
        for (i in other.indices) if (this[offset + i] != other[i]) return false
        return true
    }
}

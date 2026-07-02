package market.femi

import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsBoolean
import kotlin.js.JsModule
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.definedExternally
import org.khronos.webgl.Uint8Array
import kotlin.js.ExperimentalWasmJsInterop

// Direct binding to the project-service npm ESM (OPFS storage + XMP metadata,
// ported from the Swift ProjectService). Raw Promise-returning surface; callers
// await() and convert the Js* values.
@ExperimentalWasmJsInterop
@JsModule("project-service")
external object ProjectService {
    fun getAllGenerations(): Promise<JsArray<JsString>>
    fun getAudio(): Promise<JsString?>
    fun getPrompt(name: String): Promise<JsString?>
    fun getModel(name: String): Promise<JsString?>
    fun getSubject(name: String): Promise<JsArray<JsString>?>
    fun getLike(name: String): Promise<JsBoolean>
    fun getUrl(name: String): Promise<JsString>
    fun like(name: String, liked: Boolean): Promise<JsAny?>
    fun saveFile(
        data: Uint8Array,
        name: String,
        prompt: String? = definedExternally,
        model: String? = definedExternally,
        subject: JsArray<JsString>? = definedExternally,
    ): Promise<JsAny?>
    fun saveAudio(data: Uint8Array, name: String): Promise<JsAny?>
    fun ready(): Promise<JsAny?>
}

package market.femi.demo

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import market.femi.ProjectService

/**
 * Minimal host activity. Its only job is to demonstrate that a plain Android
 * app can consume the `:library` and bind ProjectService to a real context.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProjectService.init(applicationContext)
        setContentView(TextView(this).apply {
            text = "ProjectService storage: ${ProjectService.documents.path}"
        })
    }
}

package com.myvu.client.plugin.tasker.action

import android.app.Activity
import android.content.Intent
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.myvu.client.R
import com.myvu.client.plugin.tasker.TaskerBundleManager
import com.myvu.client.plugin.tasker.TaskerConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class TaskerActionActivityTest {

    @Test
    fun activityLoadsWithDefaultStateAndSavesHudAction() {
        val controller = Robolectric.buildActivity(TaskerActionActivity::class.java).setup()
        val activity = controller.get()

        val txtHudTitle = activity.findViewById<TextInputEditText>(R.id.txtHudTitle)
        val txtHudContent = activity.findViewById<TextInputEditText>(R.id.txtHudContent)
        val btnActionSave = activity.findViewById<MaterialButton>(R.id.btnActionSave)

        txtHudTitle.setText("Alerta Casa")
        txtHudContent.setText("Puerta abierta")

        btnActionSave.performClick()

        assertTrue(activity.isFinishing)

        val shadow = Shadows.shadowOf(activity)
        assertEquals(Activity.RESULT_OK, shadow.resultCode)

        val resultData = shadow.resultIntent
        assertNotNull(resultData)

        val bundle = resultData.getBundleExtra(TaskerConstants.EXTRA_BUNDLE)
        assertNotNull(bundle)

        val action = TaskerBundleManager.parseAction(bundle)
        assertEquals(TaskerConstants.TYPE_SHOW_HUD, action.type)
        assertEquals("Alerta Casa", action.title)
        assertEquals("Puerta abierta", action.content)

        val blurb = resultData.getStringExtra(TaskerConstants.EXTRA_BLURB)
        assertEquals("HUD: Alerta Casa - Puerta abierta", blurb)
    }

    @Test
    fun activityLoadsExistingTeleprompterBundle() {
        val initialBundle = TaskerBundleManager.buildTeleprompterBundle(
            text = "Discurso inicial",
            title = "Keynote"
        )
        val intent = Intent().apply {
            putExtra(TaskerConstants.EXTRA_BUNDLE, initialBundle)
        }

        val controller = Robolectric.buildActivity(TaskerActionActivity::class.java, intent).setup()
        val activity = controller.get()

        val txtPrompterTitle = activity.findViewById<TextInputEditText>(R.id.txtPrompterTitle)
        val txtPrompterContent = activity.findViewById<TextInputEditText>(R.id.txtPrompterContent)
        val txtBlurb = activity.findViewById<TextView>(R.id.txtBlurbPreview)

        assertEquals("Keynote", txtPrompterTitle.text.toString())
        assertEquals("Discurso inicial", txtPrompterContent.text.toString())
        assertEquals("Teleprompter: Keynote - Discurso inicial", txtBlurb.text.toString())
    }

    @Test
    fun activitySavesVariableReplaceKeysWhenVariablesPresent() {
        val controller = Robolectric.buildActivity(TaskerActionActivity::class.java).setup()
        val activity = controller.get()

        val txtHudTitle = activity.findViewById<TextInputEditText>(R.id.txtHudTitle)
        val txtHudContent = activity.findViewById<TextInputEditText>(R.id.txtHudContent)
        val btnActionSave = activity.findViewById<MaterialButton>(R.id.btnActionSave)

        txtHudTitle.setText("Aviso %sender")
        txtHudContent.setText("Mensaje: %message_text")

        btnActionSave.performClick()

        val shadow = Shadows.shadowOf(activity)
        assertEquals(Activity.RESULT_OK, shadow.resultCode)

        val resultData = shadow.resultIntent
        assertNotNull(resultData)
        val replaceKeys = resultData.getStringExtra(TaskerConstants.EXTRA_VARIABLE_REPLACE_KEYS)
        assertNotNull(replaceKeys)
        assertTrue(replaceKeys!!.contains(TaskerConstants.KEY_TITLE))
        assertTrue(replaceKeys.contains(TaskerConstants.KEY_CONTENT))
    }
}

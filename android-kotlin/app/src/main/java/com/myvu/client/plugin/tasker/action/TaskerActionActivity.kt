package com.myvu.client.plugin.tasker.action

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.myvu.client.R
import com.myvu.client.plugin.tasker.TaskerBundleManager
import com.myvu.client.plugin.tasker.TaskerConstants

class TaskerActionActivity : AppCompatActivity() {

    private lateinit var layHudTitle: TextInputLayout
    private lateinit var txtHudTitle: TextInputEditText
    private lateinit var layHudContent: TextInputLayout
    private lateinit var txtHudContent: TextInputEditText
    private lateinit var txtBlurbPreview: TextView
    private lateinit var btnActionBack: MaterialButton
    private lateinit var btnActionSave: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Myvu)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasker_action)

        initViews()
        setupListeners()

        val initialBundle = intent.getBundleExtra(TaskerConstants.EXTRA_BUNDLE)
        loadInitialState(initialBundle)
    }

    private fun initViews() {
        layHudTitle = findViewById(R.id.layHudTitle)
        txtHudTitle = findViewById(R.id.txtHudTitle)
        layHudContent = findViewById(R.id.layHudContent)
        txtHudContent = findViewById(R.id.txtHudContent)
        txtBlurbPreview = findViewById(R.id.txtBlurbPreview)
        btnActionBack = findViewById(R.id.btnActionBack)
        btnActionSave = findViewById(R.id.btnActionSave)
    }

    private fun setupListeners() {
        btnActionBack.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        btnActionSave.setOnClickListener {
            saveAndFinish()
        }

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateBlurbPreview()
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        txtHudTitle.addTextChangedListener(textWatcher)
        txtHudContent.addTextChangedListener(textWatcher)
    }

    private fun loadInitialState(bundle: Bundle?) {
        if (bundle != null) {
            val action = TaskerBundleManager.parseAction(bundle)
            txtHudTitle.setText(action.title)
            txtHudContent.setText(action.content)
        }
        updateBlurbPreview()
    }

    private fun updateBlurbPreview() {
        val title = txtHudTitle.text?.toString()?.trim() ?: ""
        val content = txtHudContent.text?.toString()?.trim() ?: ""

        val blurb = when {
            title.isNotEmpty() && content.isNotEmpty() -> "HUD: $title - $content"
            content.isNotEmpty() -> "HUD: $content"
            title.isNotEmpty() -> "HUD: $title"
            else -> "HUD: (sin mensaje)"
        }
        txtBlurbPreview.text = blurb
    }

    private fun saveAndFinish() {
        val title = txtHudTitle.text?.toString()?.trim() ?: ""
        val content = txtHudContent.text?.toString()?.trim() ?: ""

        if (content.isEmpty() && title.isEmpty()) {
            layHudContent.error = "Ingresa un mensaje para mostrar en las gafas"
            return
        }
        layHudContent.error = null

        val resultBundle = TaskerBundleManager.buildHudBundle(title, content)
        val blurb = TaskerBundleManager.generateBlurb(resultBundle)

        val resultIntent = Intent().apply {
            putExtra(TaskerConstants.EXTRA_BUNDLE, resultBundle)
            putExtra(TaskerConstants.EXTRA_BLURB, blurb)

            val replaceKeys = TaskerBundleManager.getVariableReplaceKeys(resultBundle)
            if (replaceKeys != null) {
                putExtra(TaskerConstants.EXTRA_VARIABLE_REPLACE_KEYS, replaceKeys)
            }
        }

        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}

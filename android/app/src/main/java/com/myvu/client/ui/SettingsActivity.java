package com.myvu.client.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.myvu.client.R;
import com.myvu.client.ai.AiClient;
import com.myvu.client.ai.AiProvider;
import com.myvu.client.ai.SttProvider;
import com.myvu.client.ai.TtsProvider;
import com.myvu.client.core.Prefs;
import com.myvu.client.service.ConnectionManager;
import com.myvu.client.service.MyvuService;

/** Settings for assistant providers, speech services, and notification mirroring. */
public class SettingsActivity extends AppCompatActivity {
    private TextInputLayout layApiKey;
    private TextInputLayout layModel;
    private TextInputLayout layAiEndpoint;
    private TextInputLayout laySttApiKey;
    private TextInputLayout laySttEndpoint;
    private TextInputLayout laySttModel;
    private TextInputLayout layTtsEndpoint;
    private TextInputLayout layTtsApiKey;
    private TextInputLayout layTtsModel;
    private TextInputLayout layTtsVoice;

    private TextInputEditText txtApiKey;
    private TextInputEditText txtModel;
    private TextInputEditText txtAiEndpoint;
    private TextInputEditText txtSttApiKey;
    private TextInputEditText txtSttEndpoint;
    private TextInputEditText txtSttModel;
    private TextInputEditText txtTtsEndpoint;
    private TextInputEditText txtTtsApiKey;
    private TextInputEditText txtTtsModel;
    private TextInputEditText txtTtsVoice;
    private TextInputEditText txtSystemPrompt;
    private com.google.android.material.materialswitch.MaterialSwitch chkIgnoreSsl;

    private AiProvider aiProvider;
    private SttProvider sttProvider;
    private TtsProvider ttsProvider;
    private boolean bindingAi;
    private boolean bindingStt;
    private boolean bindingTts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        bindViews();
        configureProviderSelectors();
        bindStoredValues();
        configurePersistence();
        configureButtons();
    }

    private void bindViews() {
        layApiKey = findViewById(R.id.layApiKey);
        layModel = findViewById(R.id.layModel);
        layAiEndpoint = findViewById(R.id.layAiEndpoint);
        chkIgnoreSsl = findViewById(R.id.chkIgnoreSsl);
        laySttApiKey = findViewById(R.id.laySttApiKey);
        laySttEndpoint = findViewById(R.id.laySttEndpoint);
        laySttModel = findViewById(R.id.laySttModel);
        layTtsEndpoint = findViewById(R.id.layTtsEndpoint);
        layTtsApiKey = findViewById(R.id.layTtsApiKey);
        layTtsModel = findViewById(R.id.layTtsModel);
        layTtsVoice = findViewById(R.id.layTtsVoice);

        txtApiKey = findViewById(R.id.txtApiKey);
        txtModel = findViewById(R.id.txtModel);
        txtAiEndpoint = findViewById(R.id.txtAiEndpoint);
        txtSttApiKey = findViewById(R.id.txtSttApiKey);
        txtSttEndpoint = findViewById(R.id.txtSttEndpoint);
        txtSttModel = findViewById(R.id.txtSttModel);
        txtTtsEndpoint = findViewById(R.id.txtTtsEndpoint);
        txtTtsApiKey = findViewById(R.id.txtTtsApiKey);
        txtTtsModel = findViewById(R.id.txtTtsModel);
        txtTtsVoice = findViewById(R.id.txtTtsVoice);
        txtSystemPrompt = findViewById(R.id.txtSystemPrompt);
    }

    private final View.OnClickListener providerClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            aiProvider = aiProviderFor(v.getId());
            Prefs.setAiProvider(SettingsActivity.this, aiProvider.id);
            bindAiFields();
        }
    };

    private void configureProviderSelectors() {
        aiProvider = AiProvider.fromId(Prefs.aiProvider(this));
        int[] buttonIds = new int[] {
            R.id.btnProviderAssistant, R.id.btnProviderGemini, R.id.btnProviderOpenai,
            R.id.btnProviderClaude, R.id.btnProviderGroq, R.id.btnProviderNvidia,
            R.id.btnProviderLocal
        };
        for (int id : buttonIds) {
            View btn = findViewById(id);
            if (btn != null) btn.setOnClickListener(providerClickListener);
        }

        sttProvider = SttProvider.fromId(Prefs.sttProvider(this));
        MaterialButtonToggleGroup sttGroup = findViewById(R.id.btnSttProviderGroup);
        sttGroup.check(sttProvider == SttProvider.LOCAL
                ? R.id.btnSttLocal : R.id.btnSttGroq);
        sttGroup.addOnButtonCheckedListener(new MaterialButtonToggleGroup.OnButtonCheckedListener() {
            @Override
            public void onButtonChecked(MaterialButtonToggleGroup group, int checkedId,
                                        boolean isChecked) {
                if (!isChecked) return;
                sttProvider = checkedId == R.id.btnSttLocal
                        ? SttProvider.LOCAL : SttProvider.GROQ;
                Prefs.setSttProvider(SettingsActivity.this, sttProvider.id);
                bindSttFields();
            }
        });

        ttsProvider = TtsProvider.fromId(Prefs.ttsProvider(this));
        MaterialButtonToggleGroup ttsGroup = findViewById(R.id.btnTtsProviderGroup);
        ttsGroup.check(ttsProvider == TtsProvider.HTTP
                ? R.id.btnTtsHttp : R.id.btnTtsSystem);
        ttsGroup.addOnButtonCheckedListener(new MaterialButtonToggleGroup.OnButtonCheckedListener() {
            @Override
            public void onButtonChecked(MaterialButtonToggleGroup group, int checkedId,
                                        boolean isChecked) {
                if (!isChecked) return;
                ttsProvider = checkedId == R.id.btnTtsHttp
                        ? TtsProvider.HTTP : TtsProvider.SYSTEM;
                Prefs.setTtsProvider(SettingsActivity.this, ttsProvider.id);
                bindTtsFields();
            }
        });
    }

    private void bindStoredValues() {
        bindAiFields();
        bindSttFields();
        bindTtsFields();
        String prompt = Prefs.systemPrompt(this);
        txtSystemPrompt.setText(prompt.isEmpty() ? AiClient.DEFAULT_SYSTEM_PROMPT : prompt);
    }

    private void configurePersistence() {
        persist(txtApiKey, new Saver() {
            @Override public void save(String value) {
                if (!bindingAi) Prefs.setAiApiKey(SettingsActivity.this, aiProvider.id, value);
            }
        });
        persist(txtModel, new Saver() {
            @Override public void save(String value) {
                if (!bindingAi) Prefs.setAiModel(
                        SettingsActivity.this, aiProvider.id, value.trim());
            }
        });
        persist(txtAiEndpoint, new Saver() {
            @Override public void save(String value) {
                if (!bindingAi) Prefs.setAiEndpoint(
                        SettingsActivity.this, aiProvider.id, value.trim());
            }
        });
        persist(txtSttApiKey, new Saver() {
            @Override public void save(String value) {
                if (!bindingStt) Prefs.setSttApiKey(
                        SettingsActivity.this, sttProvider.id, value);
            }
        });
        persist(txtSttEndpoint, new Saver() {
            @Override public void save(String value) {
                if (!bindingStt) Prefs.setSttEndpoint(
                        SettingsActivity.this, sttProvider.id, value.trim());
            }
        });
        persist(txtSttModel, new Saver() {
            @Override public void save(String value) {
                if (!bindingStt) Prefs.setSttModel(
                        SettingsActivity.this, sttProvider.id, value.trim());
            }
        });
        persist(txtTtsEndpoint, new Saver() {
            @Override public void save(String value) {
                if (!bindingTts) Prefs.setTtsEndpoint(SettingsActivity.this, value.trim());
            }
        });
        persist(txtTtsApiKey, new Saver() {
            @Override public void save(String value) {
                if (!bindingTts) Prefs.setTtsApiKey(SettingsActivity.this, value);
            }
        });
        persist(txtTtsModel, new Saver() {
            @Override public void save(String value) {
                if (!bindingTts) Prefs.setTtsModel(SettingsActivity.this, value.trim());
            }
        });
        persist(txtTtsVoice, new Saver() {
            @Override public void save(String value) {
                if (!bindingTts) Prefs.setTtsVoice(SettingsActivity.this, value.trim());
            }
        });
        persist(txtSystemPrompt, new Saver() {
            @Override public void save(String value) {
                Prefs.setSystemPrompt(SettingsActivity.this,
                        value.trim().equals(AiClient.DEFAULT_SYSTEM_PROMPT) ? "" : value);
            }
        });
        if (chkIgnoreSsl != null) {
            chkIgnoreSsl.setOnCheckedChangeListener((buttonView, isChecked) -> {
                Prefs.setIgnoreSsl(SettingsActivity.this, isChecked);
            });
        }
    }

    private void configureButtons() {
        findViewById(R.id.btnResetPrompt).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                txtSystemPrompt.setText(AiClient.DEFAULT_SYSTEM_PROMPT);
                Prefs.setSystemPrompt(SettingsActivity.this, "");
            }
        });
        wireWeather();
        wireMirror();
        wireGlassesSettings();
        wireLogging();
        wireTouchpad();
        findViewById(R.id.btnPickApps).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                startActivity(new Intent(SettingsActivity.this, NotificationAppsActivity.class));
            }
        });
        findViewById(R.id.btnSettingsBack).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { finish(); }
        });
    }

    private void wireTouchpad() {
        MaterialButtonToggleGroup grp = findViewById(R.id.btnTouchpadGroup);
        if (grp == null) return;

        String action = Prefs.touchpadLongPressAction(this);
        int checkedId = R.id.btnTouchpadAi;
        if (com.myvu.client.app.feature.TouchGestureManager.ACTION_MEDIA_PLAY_PAUSE.equals(action)) {
            checkedId = R.id.btnTouchpadMedia;
        } else if (com.myvu.client.app.feature.TouchGestureManager.ACTION_WEATHER_SYNC.equals(action)) {
            checkedId = R.id.btnTouchpadWeather;
        } else if (com.myvu.client.app.feature.TouchGestureManager.ACTION_TOGGLE_MIRROR.equals(action)) {
            checkedId = R.id.btnTouchpadMirror;
        }
        grp.check(checkedId);

        grp.addOnButtonCheckedListener((group, id, isChecked) -> {
            if (!isChecked) return;
            String selectedAction = com.myvu.client.app.feature.TouchGestureManager.ACTION_AI_ASSISTANT;
            if (id == R.id.btnTouchpadMedia) {
                selectedAction = com.myvu.client.app.feature.TouchGestureManager.ACTION_MEDIA_PLAY_PAUSE;
            } else if (id == R.id.btnTouchpadWeather) {
                selectedAction = com.myvu.client.app.feature.TouchGestureManager.ACTION_WEATHER_SYNC;
            } else if (id == R.id.btnTouchpadMirror) {
                selectedAction = com.myvu.client.app.feature.TouchGestureManager.ACTION_TOGGLE_MIRROR;
            }
            Prefs.setTouchpadLongPressAction(SettingsActivity.this, selectedAction);
        });
    }

    private void wireLogging() {
        MaterialSwitch sw = findViewById(R.id.swLogging);
        if (sw != null) {
            sw.setChecked(Prefs.loggingEnabled(this));
            sw.setOnCheckedChangeListener((b, checked) -> {
                Prefs.setLoggingEnabled(SettingsActivity.this, checked);
            });
        }
    }

    private void wireMirror() {
        MaterialSwitch sw = findViewById(R.id.swMirror);
        if (sw != null) {
            sw.setChecked(Prefs.mirrorEnabled(this));
            sw.setOnCheckedChangeListener((b, checked) -> {
                Prefs.setMirrorEnabled(SettingsActivity.this, checked);
            });
        }

        com.google.android.material.slider.Slider sliderNotifDuration = findViewById(R.id.sliderNotifDuration);
        TextView lblNotifDuration = findViewById(R.id.lblNotifDuration);
        int currentNotifDuration = com.myvu.client.core.GlassesConfig.getNotificationDuration(this);

        if (sliderNotifDuration != null) {
            sliderNotifDuration.setValue(currentNotifDuration);
            if (lblNotifDuration != null) {
                lblNotifDuration.setText("Notification display time: " + currentNotifDuration + "s (Default: 5s)");
            }
            sliderNotifDuration.addOnChangeListener((slider, value, fromUser) -> {
                int val = (int) value;
                com.myvu.client.core.GlassesConfig.setNotificationDuration(SettingsActivity.this, val);
                if (lblNotifDuration != null) {
                    lblNotifDuration.setText("Notification display time: " + val + "s (Default: 5s)");
                }
            });
        }
    }

    private void wireGlassesSettings() {
        com.google.android.material.slider.Slider sliderBrightness = findViewById(R.id.sliderBrightness);
        com.google.android.material.slider.Slider sliderVolume = findViewById(R.id.sliderVolume);
        TextView lblBrightness = findViewById(R.id.lblBrightness);
        TextView lblVolume = findViewById(R.id.lblVolume);

        int currentBrightness = com.myvu.client.core.GlassesConfig.getBrightness(this);
        int currentVolume = com.myvu.client.core.GlassesConfig.getVolume(this);

        if (sliderBrightness != null) {
            sliderBrightness.setValue(currentBrightness);
            if (lblBrightness != null) {
                lblBrightness.setText("Display brightness: " + currentBrightness + " (Default: 3)");
            }
            sliderBrightness.addOnChangeListener((slider, value, fromUser) -> {
                int val = (int) value;
                com.myvu.client.core.GlassesConfig.setBrightness(SettingsActivity.this, val);
                if (lblBrightness != null) {
                    lblBrightness.setText("Display brightness: " + val + " (Default: 3)");
                }
            });
        }

        if (sliderVolume != null) {
            sliderVolume.setValue(currentVolume);
            if (lblVolume != null) {
                lblVolume.setText("Glasses volume: " + currentVolume + " (Default: 11)");
            }
            sliderVolume.addOnChangeListener((slider, value, fromUser) -> {
                int val = (int) value;
                com.myvu.client.core.GlassesConfig.setVolume(SettingsActivity.this, val);
                if (lblVolume != null) {
                    lblVolume.setText("Glasses volume: " + val + " (Default: 11)");
                }
            });
        }

        com.google.android.material.slider.Slider sliderStandbyPos = findViewById(R.id.sliderStandbyPos);
        TextView lblStandbyPos = findViewById(R.id.lblStandbyPos);
        int currentStandbyPos = com.myvu.client.core.GlassesConfig.getStandbyPosition(this);

        if (sliderStandbyPos != null) {
            sliderStandbyPos.setValue(currentStandbyPos);
            if (lblStandbyPos != null) {
                lblStandbyPos.setText("Widget FOV position: " + currentStandbyPos + " (Default: 0)");
            }
            sliderStandbyPos.addOnChangeListener((slider, value, fromUser) -> {
                int val = (int) value;
                com.myvu.client.core.GlassesConfig.setStandbyPosition(SettingsActivity.this, val);
                if (lblStandbyPos != null) {
                    lblStandbyPos.setText("Widget FOV position: " + val + " (Default: 0)");
                }
            });
        }

        com.google.android.material.slider.Slider sliderScreenOff = findViewById(R.id.sliderScreenOff);
        TextView lblScreenOff = findViewById(R.id.lblScreenOff);
        int currentScreenOff = com.myvu.client.core.GlassesConfig.getScreenOffTime(this);

        if (sliderScreenOff != null) {
            sliderScreenOff.setValue(currentScreenOff);
            if (lblScreenOff != null) {
                lblScreenOff.setText("Screen active time: " + currentScreenOff + "s (Default: 10s)");
            }
            sliderScreenOff.addOnChangeListener((slider, value, fromUser) -> {
                int val = (int) value;
                com.myvu.client.core.GlassesConfig.setScreenOffTime(SettingsActivity.this, val);
                if (lblScreenOff != null) {
                    lblScreenOff.setText("Screen active time: " + val + "s (Default: 10s)");
                }
            });
        }
    }

    private void bindAiFields() {
        bindingAi = true;
        boolean local = aiProvider == AiProvider.LOCAL;
        boolean assistant = aiProvider == AiProvider.ASSISTANT;

        int selectedId = aiButtonFor(aiProvider);
        int[] buttonIds = new int[] {
            R.id.btnProviderAssistant, R.id.btnProviderGemini, R.id.btnProviderOpenai,
            R.id.btnProviderClaude, R.id.btnProviderGroq, R.id.btnProviderNvidia,
            R.id.btnProviderLocal
        };
        for (int id : buttonIds) {
            com.google.android.material.button.MaterialButton btn = findViewById(id);
            if (btn != null) {
                btn.setAlpha(id == selectedId ? 1.0f : 0.45f);
            }
        }

        layApiKey.setVisibility(assistant ? View.GONE : View.VISIBLE);
        layModel.setVisibility(assistant ? View.GONE : View.VISIBLE);
        layAiEndpoint.setVisibility(local ? View.VISIBLE : View.GONE);
        if (chkIgnoreSsl != null) {
            chkIgnoreSsl.setVisibility(local ? View.VISIBLE : View.GONE);
            chkIgnoreSsl.setChecked(Prefs.ignoreSsl(this));
        }

        layApiKey.setHint(aiProvider.label + " API key");
        layApiKey.setHelperText(local
                ? "Optional Bearer token"
                : "Create one at " + aiProvider.console);
        layModel.setHelperText(local
                ? "Required; use a model id exposed by the local server"
                : "Blank uses " + aiProvider.defaultModel);
        txtApiKey.setText(Prefs.aiApiKey(this, aiProvider.id));
        txtModel.setText(Prefs.aiModel(this, aiProvider.id));
        txtAiEndpoint.setText(Prefs.aiEndpoint(this, aiProvider.id));
        bindingAi = false;
    }

    private void bindSttFields() {
        bindingStt = true;
        boolean local = sttProvider == SttProvider.LOCAL;
        laySttApiKey.setHint(sttProvider.label + " API key");
        laySttApiKey.setHelperText(local ? "Optional Bearer token" : "Create one at console.groq.com");
        laySttEndpoint.setVisibility(local ? View.VISIBLE : View.GONE);
        laySttModel.setHelperText("Blank uses " + sttProvider.defaultModel);
        txtSttApiKey.setText(Prefs.sttApiKey(this, sttProvider.id));
        txtSttEndpoint.setText(Prefs.sttEndpoint(this, sttProvider.id));
        txtSttModel.setText(Prefs.sttModel(this, sttProvider.id));
        bindingStt = false;
    }

    private void bindTtsFields() {
        bindingTts = true;
        boolean http = ttsProvider == TtsProvider.HTTP;
        int visibility = http ? View.VISIBLE : View.GONE;
        layTtsEndpoint.setVisibility(visibility);
        layTtsApiKey.setVisibility(visibility);
        layTtsModel.setVisibility(visibility);
        layTtsVoice.setVisibility(visibility);
        txtTtsEndpoint.setText(Prefs.ttsEndpoint(this));
        txtTtsApiKey.setText(Prefs.ttsApiKey(this));
        txtTtsModel.setText(Prefs.ttsModel(this));
        txtTtsVoice.setText(Prefs.ttsVoice(this));
        bindingTts = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        int count = Prefs.allowedPackages(this).size();
        ((TextView) findViewById(R.id.txtAllowedSummary)).setText(count == 0
                ? "No apps selected — nothing is mirrored"
                : count + " app" + (count == 1 ? "" : "s") + " selected");
    }

    /**
     * The weather card. Uses MyvuService.activeConnection() rather than binding
     * the service, the same way MirrorNotificationListener does -- this screen
     * is otherwise pure SharedPreferences and does not need a binding.
     */
    private void wireWeather() {
        MaterialSwitch sw = findViewById(R.id.swWeather);
        TextInputEditText place = findViewById(R.id.txtWeatherPlace);
        com.google.android.material.slider.Slider sliderInterval = findViewById(R.id.sliderWeatherInterval);
        TextView lblInterval = findViewById(R.id.lblWeatherInterval);

        sw.setChecked(Prefs.weatherEnabled(this));
        place.setText(Prefs.weatherPlace(this));

        int currentInterval = Prefs.weatherIntervalMinutes(this);
        if (sliderInterval != null) {
            sliderInterval.setValue(currentInterval);
            if (lblInterval != null) {
                lblInterval.setText("Sync interval: " + currentInterval + " min (Default: 60 min)");
            }
            sliderInterval.addOnChangeListener((slider, value, fromUser) -> {
                int min = (int) value;
                Prefs.setWeatherIntervalMinutes(SettingsActivity.this, min);
                if (lblInterval != null) {
                    lblInterval.setText("Sync interval: " + min + " min (Default: 60 min)");
                }
            });
        }

        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                Prefs.setWeatherEnabled(SettingsActivity.this, checked);
                // Switching off leaves the cycle to lapse on its next tick;
                // switching on has to restart it, since a disabled refresh()
                // returns without rescheduling.
                if (checked) syncWeatherNow(false);
            }
        });
        persist(place, new Saver() {
            @Override public void save(String v) {
                Prefs.setWeatherPlace(SettingsActivity.this, v);
            }
        });
        findViewById(R.id.btnSyncWeather).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { syncWeatherNow(true); }
        });
    }

    private void syncWeatherNow(boolean announce) {
        ConnectionManager c = MyvuService.activeConnection();
        if (c == null) {
            if (announce) {
                Toast.makeText(this, "Connect to the glasses first", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        c.syncWeatherNow();
        if (announce) Toast.makeText(this, "Syncing weather…", Toast.LENGTH_SHORT).show();
    }

    private static int aiButtonFor(AiProvider provider) {
        switch (provider) {
            case OPENAI: return R.id.btnProviderOpenai;
            case GEMINI: return R.id.btnProviderGemini;
            case GROQ: return R.id.btnProviderGroq;
            case NVIDIA: return R.id.btnProviderNvidia;
            case ASSISTANT: return R.id.btnProviderAssistant;
            case LOCAL: return R.id.btnProviderLocal;
            default: return R.id.btnProviderClaude;
        }
    }

    private static AiProvider aiProviderFor(int buttonId) {
        if (buttonId == R.id.btnProviderOpenai) return AiProvider.OPENAI;
        if (buttonId == R.id.btnProviderGemini) return AiProvider.GEMINI;
        if (buttonId == R.id.btnProviderGroq) return AiProvider.GROQ;
        if (buttonId == R.id.btnProviderNvidia) return AiProvider.NVIDIA;
        if (buttonId == R.id.btnProviderAssistant) return AiProvider.ASSISTANT;
        if (buttonId == R.id.btnProviderLocal) return AiProvider.LOCAL;
        return AiProvider.CLAUDE;
    }

    private interface Saver {
        void save(String value);
    }

    private static void persist(TextView field, final Saver saver) {
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable text) { saver.save(text.toString()); }
        });
    }
}

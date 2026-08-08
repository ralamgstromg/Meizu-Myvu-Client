package com.myvu.client.app;

import android.content.Context;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.myvu.client.app.feature.Notifications;
import com.myvu.client.app.feature.TouchGestureManager;
import com.myvu.client.core.LogBus;
import com.myvu.client.core.Prefs;

import org.json.JSONObject;

/**
 * Handles incoming events registered with InboundRouter (AI triggers, weather requests,
 * and battery updates).
 */
public class GlassesEventHandler {

    public interface Delegate {
        void wakeRelay();
        void triggerAi(int triggerCode);
        void pageClosed();
        void refreshWeather();
        void updateBattery(int battery, boolean isCharging);
        void sendAction(String actionJson);
    }

    private final Context context;
    private final InboundRouter inbound;
    private final Delegate delegate;

    public GlassesEventHandler(Context context, InboundRouter inbound, Delegate delegate) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.inbound = inbound;
        this.delegate = delegate;
        installListeners();
    }

    private void installListeners() {
        inbound.setAiTriggerListener(new InboundRouter.AiTriggerListener() {
            @Override
            public void onAiTrigger(int code, JSONObject payload) {
                if (payload != null && payload.optInt("control", 1) == 0) {
                    delegate.pageClosed();
                    return;
                }
                delegate.wakeRelay();

                TouchGestureManager.handleTrigger(context, code, new TouchGestureManager.ActionExecutor() {
                    @Override
                    public void executeAiAssistant(int triggerCode) {
                        delegate.triggerAi(triggerCode);
                    }

                    @Override
                    public void executeWeatherSync() {
                        delegate.refreshWeather();
                        try {
                            delegate.sendAction(Notifications.buildShow("MYVU", "Actualizando clima..."));
                        } catch (Exception ignored) {}
                    }

                    @Override
                    public void executeToggleMirror() {
                        if (context == null) return;
                        boolean enabled = !Prefs.mirrorEnabled(context);
                        Prefs.setMirrorEnabled(context, enabled);
                        LogBus.log("Touchpad gesture -> Notification mirroring " + (enabled ? "ON" : "OFF"));
                        try {
                            delegate.sendAction(Notifications.buildShow("MYVU", "Espejo notificaciones: " + (enabled ? "Activado" : "Desactivado")));
                        } catch (Exception ignored) {}
                    }

                    @Override
                    public void executeMediaPlayPause() {
                        LogBus.log("Touchpad gesture -> Media Play/Pause");
                        if (context != null) {
                            try {
                                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                                if (am != null) {
                                    long now = SystemClock.uptimeMillis();
                                    am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
                                    am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
                                }
                            } catch (Exception e) {
                                LogBus.error("could not send media play/pause key event", e);
                            }
                        }
                        try {
                            delegate.sendAction(Notifications.buildShow("MYVU", "Música: Play / Pausa"));
                        } catch (Exception ignored) {}
                    }
                });
            }
        });

        inbound.setWeatherRequestListener(new InboundRouter.WeatherRequestListener() {
            @Override
            public void onWeatherRequested() {
                delegate.refreshWeather();
            }
        });

        inbound.setBatteryUpdateListener(new InboundRouter.BatteryUpdateListener() {
            @Override
            public void onBatteryUpdated(int battery, boolean isCharging) {
                delegate.updateBattery(battery, isCharging);
            }
        });
    }
}

# Graph Report - android  (2026-08-08)

## Corpus Check
- 131 files · ~96,851 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1594 nodes · 4151 edges · 78 communities (53 shown, 25 thin omitted)
- Extraction: 93% EXTRACTED · 7% INFERRED · 0% AMBIGUOUS · INFERRED: 300 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Community 0: RelaySession.java, Entry
- Community 1: Hex.java, Hex
- Community 2: PhoneActionExecutor
- Community 3: NavCommands.java, NavCommands
- Community 4: AbilityReply.java
- Community 5: android.graphics.Canvas, android.graphics.Paint
- Community 6: android.content.Context
- Community 7: AckCallback
- Community 8: android.service.notification.NotificationListenerService, android.service.notification.StatusBarNotification
- Community 9: Animator, ConnectActivity
- Community 10: AiTriggerListener
- Community 11: FusedLocationSource.java, FusedLocationSource
- Community 12: ConnectionManager
- Community 13: Adapter, android.graphics.drawable.Drawable
- Community 14: AppLayer.java, AppLayer
- Community 15: RelaySession
- Community 16: android.bluetooth.BluetoothA2dp, android.bluetooth.BluetoothDevice
- Community 17: android.content.ServiceConnection, android.content.SharedPreferences
- Community 18: android.app.Notification, android.app.Service
- Community 19: 
- Community 20: android.bluetooth.BluetoothGattCallback, android.bluetooth.BluetoothGattCharacteristic
- Community 21: android.media.AudioManager, android.os.Handler
- Community 22: HttpCache.java, CacheEntry
- Community 23: BleHeartbeat
- Community 24: GlassesConfig
- Community 25: 
- Community 26: BleReassembler
- Community 27: BufferPool.java
- Community 28: HttpRetry
- Community 29: OpenAiTranscriptionClient.java, OpenAiTranscriptionClient
- Community 30: android.bluetooth.BluetoothAdapter, android.bluetooth.le.BluetoothLeScanner
- Community 31: Day, JSONObject
- Community 32: android.media.MediaPlayer, android.speech.tts.TextToSpeech
- Community 33: AiConversation
- Community 34: JSONObject, SystemSettings
- Community 35: android.bluetooth.BluetoothSocket
- Community 36: 
- Community 37: android.bluetooth.BluetoothGatt, GattOp.java
- Community 38: android.speech.SpeechRecognizer, SttSource.java
- Community 39: 
- Community 40: 
- Community 41: AiProtocolTest.java, AiProtocolTest
- Community 42: AiProtocol
- Community 43: JSONObject
- Community 44: TouchGestureManager.java, ActionExecutor
- Community 45: 
- Community 47: android.media.MediaCodec
- Community 48: ConnectionState
- Community 49: AiHttpClient
- Community 50: GlassesMicStream
- Community 51: AiProvider.java
- Community 52: Listener
- Community 53: AiClient.java, AiClient
- Community 54: AiHttpClient.java, HttpEndpoint.java
- Community 55: ClockSync.java, ClockSync
- Community 56: Session.java, JSONObject
- Community 57: BleHeartbeatTest
- Community 59: WeatherCodes.java, Condition
- Community 61: Override, LocalAiClient
- Community 62: Handler
- Community 63: ClaudeClient
- Community 64: GeminiClient
- Community 65: HttpTtsClient
- Community 66: Override, OpenAiClient
- Community 67: 
- Community 68: SslUtils.java, SslUtils
- Community 70: ClaudeClient.java, GeminiClient.java
- Community 71: FusedLocationSourceTest.java
- Community 72: OpenMeteo
- Community 73: SttProvider.java, fromId()
- Community 74: TtsProvider.java, fromId()
- Community 75: Handler
- Community 76: gradlew, gradlew script

## God Nodes (most connected - your core abstractions)
1. `ConnectionManager` - 127 edges
2. `Prefs` - 86 edges
3. `ConnectActivity` - 54 edges
4. `LogBus` - 48 edges
5. `BleTransport` - 39 edges
6. `AiConversation` - 34 edges
7. `BleMessageChannel` - 32 edges
8. `SettingsActivity` - 29 edges
9. `PhoneActionExecutor` - 26 edges
10. `MyvuService` - 24 edges

## Surprising Connections (you probably didn't know these)
- `AiHttpClient` --implements--> `AiClient`  [EXTRACTED]
  app/src/main/java/com/myvu/client/ai/AiHttpClient.java → app/src/main/java/com/myvu/client/ai/AiClient.java
- `AiConversation` --references--> `GlassesMicStream`  [EXTRACTED]
  app/src/main/java/com/myvu/client/ai/AiConversation.java → app/src/main/java/com/myvu/client/ai/GlassesMicStream.java
- `AiConversation` --references--> `OpusDecoderStream`  [EXTRACTED]
  app/src/main/java/com/myvu/client/ai/AiConversation.java → app/src/main/java/com/myvu/client/ai/OpusDecoderStream.java
- `AiConversation` --references--> `PhoneActionExecutor`  [EXTRACTED]
  app/src/main/java/com/myvu/client/ai/AiConversation.java → app/src/main/java/com/myvu/client/ai/PhoneActionExecutor.java
- `AiConversation` --references--> `TtsPlayer`  [EXTRACTED]
  app/src/main/java/com/myvu/client/ai/AiConversation.java → app/src/main/java/com/myvu/client/ai/TtsPlayer.java

## Import Cycles
- None detected.

## Communities (78 total, 25 thin omitted)

### Community 0 - "Community 0: RelaySession.java, Entry"
Cohesion: 0.06
Nodes (13): Entry, InitBurst, Relay, RelayMessage, RelaySequencer, TlvBox, TlvTags, FrameReassembler (+5 more)

### Community 1 - "Community 1: Hex.java, Hex"
Cohesion: 0.06
Nodes (20): Hex, EcKeyPair, StarryCrypto, DeviceId, LinkCommands, LinkMessage, LinkProtocol, MsgType (+12 more)

### Community 2 - "Community 2: PhoneActionExecutor"
Cohesion: 0.07
Nodes (5): PhoneActionExecutor, NavSession, Sender, LogBusTest, org.junit.Before

### Community 3 - "Community 3: NavCommands.java, NavCommands"
Cohesion: 0.06
Nodes (11): NavCommands, Geo, IcMap, Step, Osrm, Route, Step, Vertex (+3 more)

### Community 4 - "Community 4: AbilityReply.java"
Cohesion: 0.08
Nodes (7): AbilityReply, DeviceInfo, Override, Pb, Override, PbValue, PbTest

### Community 5 - "Community 5: android.graphics.Canvas, android.graphics.Paint"
Cohesion: 0.07
Nodes (13): android.graphics.Canvas, android.graphics.Paint, android.util.AttributeSet, android.view.GestureDetector, android.view.MotionEvent, JSONObject, Trackpad, Override (+5 more)

### Community 7 - "Community 7: AckCallback"
Cohesion: 0.10
Nodes (5): AckCallback, BleMessageChannel, Receiver, Writer, BlePackets

### Community 8 - "Community 8: android.service.notification.NotificationListenerService, android.service.notification.StatusBarNotification"
Cohesion: 0.08
Nodes (12): android.service.notification.NotificationListenerService, android.service.notification.StatusBarNotification, JSONObject, Notifications, Handler, Override, MirrorNotificationListener, Clock (+4 more)

### Community 9 - "Community 9: Animator, ConnectActivity"
Cohesion: 0.09
Nodes (8): Animator, ConnectActivity, Override, Saver, SystemToggleSetter, Toggle, com.google.android.material.button.MaterialButton, ImageView

### Community 10 - "Community 10: AiTriggerListener"
Cohesion: 0.11
Nodes (8): AiTriggerListener, BatteryUpdateListener, InboundRouter, JSONObject, Sender, WeatherRequestListener, InboundRouterTest, Sent

### Community 11 - "Community 11: FusedLocationSource.java, FusedLocationSource"
Cohesion: 0.09
Nodes (10): FusedLocationSource, Override, Listener, LocationSource, Sender, WeatherSync, com.google.android.gms.location.FusedLocationProviderClient, com.google.android.gms.location.LocationCallback (+2 more)

### Community 13 - "Community 13: Adapter, android.graphics.drawable.Drawable"
Cohesion: 0.12
Nodes (13): Adapter, android.graphics.drawable.Drawable, android.view.ViewGroup, androidx.annotation.NonNull, Override, TextView, LogAdapter, Row (+5 more)

### Community 14 - "Community 14: AppLayer.java, AppLayer"
Cohesion: 0.12
Nodes (3): AppLayer, Teleprompter, JsonShapeTest

### Community 15 - "Community 15: RelaySession"
Cohesion: 0.12
Nodes (3): RelaySession, Transport, TransportListener

### Community 16 - "Community 16: android.bluetooth.BluetoothA2dp, android.bluetooth.BluetoothDevice"
Cohesion: 0.14
Nodes (9): android.bluetooth.BluetoothA2dp, android.bluetooth.BluetoothDevice, android.bluetooth.BluetoothHeadset, android.bluetooth.BluetoothProfile, android.content.BroadcastReceiver, AudioProfiles, Listener, Bonding (+1 more)

### Community 17 - "Community 17: android.content.ServiceConnection, android.content.SharedPreferences"
Cohesion: 0.15
Nodes (14): android.content.ServiceConnection, android.content.SharedPreferences, android.os.Bundle, android.os.Vibrator, android.view.View, android.widget.ImageView, android.widget.ProgressBar, android.widget.TextView (+6 more)

### Community 18 - "Community 18: android.app.Notification, android.app.Service"
Cohesion: 0.14
Nodes (9): android.app.Notification, android.app.Service, android.content.Intent, android.os.Binder, android.os.IBinder, Intent, Override, LocalBinder (+1 more)

### Community 19 - "Community 19: "
Cohesion: 0.13
Nodes (3): Saver, SettingsActivity, OnClickListener

### Community 20 - "Community 20: android.bluetooth.BluetoothGattCallback, android.bluetooth.BluetoothGattCharacteristic"
Cohesion: 0.14
Nodes (4): android.bluetooth.BluetoothGattCallback, android.bluetooth.BluetoothGattCharacteristic, android.os.HandlerThread, BleTransport

### Community 21 - "Community 21: android.media.AudioManager, android.os.Handler"
Cohesion: 0.20
Nodes (5): android.media.AudioManager, android.os.Handler, Handler, LogBus, java.text.SimpleDateFormat

### Community 22 - "Community 22: HttpCache.java, CacheEntry"
Cohesion: 0.18
Nodes (3): CacheEntry, HttpCache, HttpCacheTest

### Community 23 - "Community 23: BleHeartbeat"
Cohesion: 0.17
Nodes (5): BleHeartbeat, HandlerScheduler, Override, Scheduler, TimeProvider

### Community 26 - "Community 26: BleReassembler"
Cohesion: 0.19
Nodes (3): BleReassembler, Uuids, BleReassemblerTest

### Community 27 - "Community 27: BufferPool.java"
Cohesion: 0.20
Nodes (3): BufferPool, BufferPoolTest, java.util.concurrent.ConcurrentLinkedQueue

### Community 28 - "Community 28: HttpRetry"
Cohesion: 0.17
Nodes (4): HttpRetry, NonRetryableHttpException, Request, HttpRetryTest

### Community 29 - "Community 29: OpenAiTranscriptionClient.java, OpenAiTranscriptionClient"
Cohesion: 0.16
Nodes (4): OpenAiTranscriptionClient, OpusStream, DataOutputStream, java.io.DataOutputStream

### Community 30 - "Community 30: android.bluetooth.BluetoothAdapter, android.bluetooth.le.BluetoothLeScanner"
Cohesion: 0.16
Nodes (7): android.bluetooth.BluetoothAdapter, android.bluetooth.le.BluetoothLeScanner, android.bluetooth.le.ScanCallback, android.bluetooth.le.ScanResult, Callback, GlassesScanner, ScanCallback

### Community 31 - "Community 31: Day, JSONObject"
Cohesion: 0.22
Nodes (6): Day, JSONObject, Reading, Weather, JSONObject, WeatherTest

### Community 32 - "Community 32: android.media.MediaPlayer, android.speech.tts.TextToSpeech"
Cohesion: 0.18
Nodes (6): android.media.MediaPlayer, android.speech.tts.TextToSpeech, Callback, TtsPlayer, MediaPlayer, TextToSpeech

### Community 35 - "Community 35: android.bluetooth.BluetoothSocket"
Cohesion: 0.22
Nodes (4): android.bluetooth.BluetoothSocket, Override, RfcommTransport, java.util.concurrent.LinkedBlockingQueue

### Community 37 - "Community 37: android.bluetooth.BluetoothGatt, GattOp.java"
Cohesion: 0.20
Nodes (4): android.bluetooth.BluetoothGatt, GattOp, Override, GattQueue

### Community 38 - "Community 38: android.speech.SpeechRecognizer, SttSource.java"
Cohesion: 0.16
Nodes (3): android.speech.SpeechRecognizer, Listener, SttSource

### Community 39 - "Community 39: "
Cohesion: 0.14
Nodes (4): Override, onBatteryUpdated(), onWeatherRequested(), PendingAction

### Community 41 - "Community 41: AiProtocolTest.java, AiProtocolTest"
Cohesion: 0.30
Nodes (3): AiProtocolTest, JSONObject, org.junit.Test

### Community 43 - "Community 43: JSONObject"
Cohesion: 0.24
Nodes (3): JSONObject, JSONObject, SystemSettingsTest

### Community 44 - "Community 44: TouchGestureManager.java, ActionExecutor"
Cohesion: 0.22
Nodes (5): ActionExecutor, TouchGestureManager, JSONObject, onAiTrigger(), TouchGestureManagerTest

### Community 48 - "Community 48: ConnectionState"
Cohesion: 0.16
Nodes (8): ConnectionState, BONDING, CONNECTING, FAILED, IDLE, PAIRING, READY, SESSION

### Community 49 - "Community 49: AiHttpClient"
Cohesion: 0.26
Nodes (3): AiHttpClient, Override, HttpsURLConnection

### Community 51 - "Community 51: AiProvider.java"
Cohesion: 0.18
Nodes (9): AiProvider, ASSISTANT, CLAUDE, GEMINI, GROQ, LOCAL, NVIDIA, OPENAI (+1 more)

### Community 53 - "Community 53: AiClient.java, AiClient"
Cohesion: 0.20
Nodes (5): AiClient, Context, newClient(), AndroidAssistantClient, Override

### Community 56 - "Community 56: Session.java, JSONObject"
Cohesion: 0.33
Nodes (3): JSONObject, Session, JSONArray

### Community 57 - "Community 57: BleHeartbeatTest"
Cohesion: 0.33
Nodes (5): BleHeartbeatTest, Override, ScheduledTask, TestScheduler, TestTimeProvider

### Community 62 - "Community 62: Handler"
Cohesion: 0.25
Nodes (3): Handler, HandlerThread, Listener

### Community 68 - "Community 68: SslUtils.java, SslUtils"
Cohesion: 0.43
Nodes (3): SslUtils, java.net.URLConnection, javax.net.ssl.HttpsURLConnection

### Community 73 - "Community 73: SttProvider.java, fromId()"
Cohesion: 0.50
Nodes (4): fromId(), SttProvider, GROQ, LOCAL

### Community 74 - "Community 74: TtsProvider.java, fromId()"
Cohesion: 0.50
Nodes (4): fromId(), TtsProvider, HTTP, SYSTEM

### Community 75 - "Community 75: Handler"
Cohesion: 0.40
Nodes (3): Handler, HandlerThread, Listener

### Community 76 - "Community 76: gradlew, gradlew script"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

## Knowledge Gaps
- **22 isolated node(s):** `CLAUDE`, `OPENAI`, `GEMINI`, `GROQ`, `NVIDIA` (+17 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **25 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `ConnectionManager` connect `Community 12: ConnectionManager` to `Community 1: Hex.java, Hex`, `Community 2: PhoneActionExecutor`, `Community 4: AbilityReply.java`, `Community 5: android.graphics.Canvas, android.graphics.Paint`, `Community 6: android.content.Context`, `Community 9: Animator, ConnectActivity`, `Community 10: AiTriggerListener`, `Community 11: FusedLocationSource.java, FusedLocationSource`, `Community 14: AppLayer.java, AppLayer`, `Community 15: RelaySession`, `Community 16: android.bluetooth.BluetoothA2dp, android.bluetooth.BluetoothDevice`, `Community 17: android.content.ServiceConnection, android.content.SharedPreferences`, `Community 18: android.app.Notification, android.app.Service`, `Community 20: android.bluetooth.BluetoothGattCallback, android.bluetooth.BluetoothGattCharacteristic`, `Community 21: android.media.AudioManager, android.os.Handler`, `Community 24: GlassesConfig`, `Community 30: android.bluetooth.BluetoothAdapter, android.bluetooth.le.BluetoothLeScanner`, `Community 33: AiConversation`, `Community 35: android.bluetooth.BluetoothSocket`, `Community 36: `, `Community 39: `, `Community 40: `, `Community 46: `, `Community 48: ConnectionState`, `Community 75: Handler`?**
  _High betweenness centrality (0.187) - this node is a cross-community bridge._
- **Why does `ConnectActivity` connect `Community 9: Animator, ConnectActivity` to `Community 2: PhoneActionExecutor`, `Community 36: `, `Community 5: android.graphics.Canvas, android.graphics.Paint`, `Community 6: android.content.Context`, `Community 13: Adapter, android.graphics.drawable.Drawable`, `Community 48: ConnectionState`, `Community 17: android.content.ServiceConnection, android.content.SharedPreferences`, `Community 18: android.app.Notification, android.app.Service`, `Community 52: Listener`?**
  _High betweenness centrality (0.055) - this node is a cross-community bridge._
- **Why does `Prefs` connect `Community 6: android.content.Context` to `Community 1: Hex.java, Hex`, `Community 67: `, `Community 69: `, `Community 44: TouchGestureManager.java, ActionExecutor`, `Community 17: android.content.ServiceConnection, android.content.SharedPreferences`, `Community 18: android.app.Notification, android.app.Service`, `Community 19: `, `Community 21: android.media.AudioManager, android.os.Handler`, `Community 24: GlassesConfig`, `Community 58: `, `Community 60: `?**
  _High betweenness centrality (0.048) - this node is a cross-community bridge._
- **What connects `CLAUDE`, `OPENAI`, `GEMINI` to the rest of the system?**
  _22 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0: RelaySession.java, Entry` be split into smaller, more focused modules?**
  _Cohesion score 0.06234567901234568 - nodes in this community are weakly interconnected._
- **Should `Community 1: Hex.java, Hex` be split into smaller, more focused modules?**
  _Cohesion score 0.05536568694463431 - nodes in this community are weakly interconnected._
- **Should `Community 2: PhoneActionExecutor` be split into smaller, more focused modules?**
  _Cohesion score 0.07192460317460317 - nodes in this community are weakly interconnected._
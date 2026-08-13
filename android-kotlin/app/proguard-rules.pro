# ProGuard rules for Meizu MYVU Client (Kotlin)

# Keep reflection and hidden-API access used in Bonding and Bluetooth reflection
-keep class com.myvu.client.service.Bonding { *; }
-keepclassmembers class com.myvu.client.service.Bonding { *; }

-keepclassmembers class android.bluetooth.BluetoothDevice {
    public boolean createBond(int);
    public boolean removeBond();
}

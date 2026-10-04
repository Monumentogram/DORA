import android.os.BatteryManager;
import android.os.SystemClock;
import java.lang.reflect.Constructor;

/** Read-only framework probe; no microphone, files, network or battery overrides. */
public final class EnergyProbe {
    public static void main(String[] args) throws Exception {
        Constructor<BatteryManager> constructor = BatteryManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        BatteryManager manager = constructor.newInstance();
        System.out.println("runtimeEnergyPropertyId=" + BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);
        for (int i = 0; i < 3; i++) {
            long energy = manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);
            long charge = manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            System.out.println("elapsedRealtimeMs=" + SystemClock.elapsedRealtime()
                + " energyRaw=" + energy + " unsupported=" + (energy == Long.MIN_VALUE)
                + " chargeCounterMicroAh=" + charge);
            if (i < 2) Thread.sleep(2000);
        }
    }
}

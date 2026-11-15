package nu.gpu.aoigram.utils;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

import nu.gpu.aoigram.config.AoiConfig;

public final class TabletMode {

    public static final int AUTO = 0;
    public static final int ON = 1;
    public static final int OFF = 2;
    public static final int APPLIED = AoiConfig.tabletMode.get();

    public static boolean isDeviceTablet() {
        return ApplicationLoader.applicationContext.getResources().getBoolean(R.bool.isTablet);
    }

    public static String getLabel(int mode) {
        return switch (mode) {
            case ON -> getString(R.string.TabletModeOn);
            case OFF -> getString(R.string.TabletModeOff);
            default -> getString(R.string.TabletModeAuto);
        };
    }
}

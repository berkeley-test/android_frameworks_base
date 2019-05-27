package com.samsung.android.sdk.look;

import android.app.ActivityThread;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import java.util.List;

public class SlookImpl {
    private static final int AIRBUTTON = 1;
    private static final int COCKTAIL_BAR = 6;
    private static final int COCKTAIL_PANEL = 7;
    public static final boolean DEBUG = true;
    private static final int SDK_INT = SystemProperties.getInt("ro.slook.ver", 0);
    private static final int SMARTCLIP = 2;
    private static final int SPEN_HOVER_ICON = 4;
    private static final int WRITINGBUDDY = 3;
    private static int sCocktailLevel = -1;
    private static int sHasMetaEdgeSingle = -1;
    private static int sUspLevel = -1;

    public static class VERSION_CODES {
        public static final int L1 = 1;
        public static final int L2 = 2;
    }

    public static int getVersionCode() {
        return SDK_INT;
    }

    public static boolean isFeatureEnabled(int type) {
        boolean z = true;
        boolean z2 = false;
        switch (type) {
            case 1:
            case 3:
                break;
            case 2:
            case 4:
                if (VERSION.SDK_INT >= 24) {
                    return false;
                }
                break;
            case 6:
                checkCocktailLevel();
                if (sCocktailLevel > 0 && sCocktailLevel <= type) {
                    return true;
                }
                if (sCocktailLevel <= 0) {
                    return false;
                }
                checkValidCocktailMetaData();
                if (sHasMetaEdgeSingle != 1) {
                    z = false;
                }
                return z;
            case 7:
                checkCocktailLevel();
                if (sCocktailLevel > 0 && sCocktailLevel <= type) {
                    z2 = true;
                }
                return z2;
            default:
                return false;
        }
        if (!(sUspLevel == -1 && ActivityThread.getPackageManager() == null)) {
        }
        if (type == 1) {
            if (sUspLevel < 2 || sUspLevel > 3) {
                z = false;
            }
            return z;
        } else if (type == 3) {
            if (sUspLevel >= 2 && sUspLevel <= 9) {
                z2 = true;
            }
            return z2;
        } else {
            if (sUspLevel < 2) {
                z = false;
            }
            return z;
        }
    }

    private static void checkCocktailLevel() {
        int i = 0;
        if (sCocktailLevel == -1) {
            IPackageManager pm = ActivityThread.getPackageManager();
            if (pm != null) {
                try {
                    int i2;
                    if (pm.hasSystemFeature("com.sec.feature.cocktailbar", 0)) {
                        i2 = 6;
                    } else {
                        i2 = 0;
                    }
                    sCocktailLevel = i2;
                    if (sCocktailLevel == 0) {
                        if (pm.hasSystemFeature("com.sec.feature.cocktailpanel", 0)) {
                            i = 7;
                        }
                        sCocktailLevel = i;
                    }
                } catch (RemoteException e) {
                    throw new RuntimeException("Package manager has died", e);
                }
            }
        }
    }

    private static void checkValidCocktailMetaData() {
        if (sHasMetaEdgeSingle == -1) {
            sHasMetaEdgeSingle = 0;
            IPackageManager pm = ActivityThread.getPackageManager();
            String packageName = ActivityThread.currentOpPackageName();
            if (pm != null && packageName != null) {
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(packageName, 128, UserHandle.myUserId());
                    if (ai != null) {
                        String value;
                        Bundle metaData = ai.metaData;
                        if (metaData != null) {
                            value = metaData.getString("com.samsung.android.cocktail.mode", "");
                            if (value != null && value.equals("edge_single")) {
                                sHasMetaEdgeSingle = 1;
                            }
                        }
                        if (sHasMetaEdgeSingle == 0) {
                            Intent intent = new Intent("com.samsung.android.cocktail.action.COCKTAIL_UPDATE");
                            intent.setPackage(packageName);
                            intent.resolveTypeIfNeeded(ActivityThread.currentApplication().getContentResolver());
                            List<ResolveInfo> broadcastReceivers = pm.queryIntentReceivers(intent, intent.resolveTypeIfNeeded(ActivityThread.currentApplication().getContentResolver()), 128, UserHandle.myUserId()).getList();
                            int N = broadcastReceivers == null ? 0 : broadcastReceivers.size();
                            for (int i = 0; i < N; i++) {
                                ActivityInfo activityInfo = ((ResolveInfo) broadcastReceivers.get(i)).activityInfo;
                                if ((activityInfo.applicationInfo.flags & 262144) == 0 && packageName.equals(activityInfo.packageName)) {
                                    metaData = activityInfo.metaData;
                                    if (metaData != null) {
                                        value = metaData.getString("com.samsung.android.cocktail.mode", "");
                                        if (value != null && value.equals("edge_single")) {
                                            sHasMetaEdgeSingle = 1;
                                            break;
                                        }
                                    }
                                    continue;
                                }
                            }
                        }
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

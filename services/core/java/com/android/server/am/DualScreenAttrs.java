package com.android.server.am;

import android.content.ComponentName;
import com.samsung.android.dualscreen.DualScreen;

public class DualScreenAttrs {
    public static final int[] DUAL_SCREEN_OBSCURED_ZONES = new int[]{1, 2, 3, 0};
    public static final int DUAL_SCREEN_OBSCURED_ZONE_FULL = 3;
    public static final int DUAL_SCREEN_OBSCURED_ZONE_MAIN = 1;
    public static final int DUAL_SCREEN_OBSCURED_ZONE_SUB = 2;
    public static final int DUAL_SCREEN_OBSCURED_ZONE_UNKNOWN = 0;
    public static DualScreenAttrs sConstDefaultDualScreenAttrs = new DualScreenAttrs();
    private int mFinishFlag;
    private DualScreen mScreen;
    private int mStopFlag;
    public ComponentName triggerActivity;

    public DualScreenAttrs() {
        setScreen(DualScreen.MAIN);
    }

    public DualScreenAttrs(DualScreen targetScreen) {
        setScreen(targetScreen);
    }

    public DualScreenAttrs(DualScreenAttrs attrs) {
        setTo(attrs, true);
    }

    public void setScreen(DualScreen targetScreen) {
        this.mScreen = targetScreen;
    }

    public DualScreen getScreen() {
        return this.mScreen;
    }

    public int getDisplayId() {
        return this.mScreen.getDisplayId();
    }

    public boolean equals(DualScreenAttrs other) {
        return true;
    }

    public void setTo(DualScreenAttrs other) {
        setTo(other, false);
    }

    public void setTo(DualScreenAttrs other, boolean includeUniqueOptions) {
        if (other != null) {
            setScreen(other.getScreen());
        }
    }

    public void addStopFlag(int displayId) {
        this.mStopFlag |= DUAL_SCREEN_OBSCURED_ZONES[displayId];
    }

    public void clearStopFlag() {
        this.mStopFlag = 0;
    }

    public boolean okToStop() {
        return (this.mStopFlag & DUAL_SCREEN_OBSCURED_ZONES[getDisplayId()]) != 0;
    }

    public void addFinishFlag(int displayId) {
        this.mFinishFlag |= DUAL_SCREEN_OBSCURED_ZONES[displayId];
    }

    public void clearFinishFlag() {
        this.mFinishFlag = 0;
    }

    public boolean okToFinish() {
        return (this.mFinishFlag & DUAL_SCREEN_OBSCURED_ZONES[getDisplayId()]) != 0;
    }

    public String toString() {
        StringBuilder out = new StringBuilder(128);
        out.append(getClass().getSimpleName());
        out.append("{mTargetScreen=");
        out.append(this.mScreen.toString());
        out.append("(#");
        out.append(this.mScreen.getDisplayId());
        out.append(")");
        if (this.mStopFlag > 0) {
            out.append(", mStopFlag=0x");
            out.append(Integer.toBinaryString(this.mStopFlag));
        }
        if (this.mFinishFlag > 0) {
            out.append(", mFinishFlag=0x");
            out.append(Integer.toBinaryString(this.mFinishFlag));
        }
        out.append("}");
        return out.toString();
    }
}

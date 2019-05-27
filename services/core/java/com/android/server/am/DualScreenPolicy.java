package com.android.server.am;

import android.app.AppGlobals;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Settings.System;
import android.util.Log;
import android.util.secutil.Slog;
import com.android.internal.app.ChooserActivity;
import com.android.internal.app.ResolverActivity;
//import com.android.internal.app.ResolverGuideActivity;
import com.samsung.android.dualscreen.DualScreen;
import com.samsung.android.dualscreen.DualScreenManager;
import com.samsung.android.dualscreen.TaskInfo;
import com.samsung.android.multidisplay.dualscreen.DualScreenConfigs;
import com.samsung.android.multidisplay.dualscreen.DualScreenFeatures;
import com.samsung.android.multidisplay.dualscreen.DualScreenUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DualScreenPolicy {
    public static final String ACTION_FOLDING_STATE_CHANGED_INTENT = "com.samsung.android.dualscreen.action.FOLDING_STATE_CHANGED";
    private static final boolean DEBUG = DualScreenManager.DEBUG;
    public static final boolean DEFAULT_FINISH_WITH_COUPLED_TASK = true;
    public static final String EXTRA_FOLDING_STATE = "com.samsung.android.dualscreen.extra.FOLDING_STATE";
    static final int FLAG_EXPANEDED_HOME_NONE = 0;
    static final int FLAG_EXPANEDED_HOME_ON_EXPANEDED = 3;
    static final int FLAG_EXPANEDED_HOME_ON_MAIN = 1;
    static final int FLAG_EXPANEDED_HOME_ON_SUB = 2;
    static final int[] FLAG_EXPANEDED_HOME_STATUS = new int[]{1, 2, 3};
    static final int MULTIPLE_SCREEN_STATE_CHANGED_MSG = 0;
    private static final String TAG = "DualScreenPolicy";
    public static final String USE_INTERNAL_APIS = "com.samsung.android.dualscreen.permission.USE_INTERNAL_APIS";
    public static final int WAKE_UP_2_FINGER = 4;
    public static final int WAKE_UP_REASON_APP_LAUNCHING_BACK_SCREEN = 8;
    public static final int WAKE_UP_REASON_BACK_SCREEN = 5;
    public static final int WAKE_UP_REASON_COVER_OPEN = 1;
    public static final int WAKE_UP_REASON_FLIP_SENSOR = 2;
    public static final int WAKE_UP_REASON_POWER_STATE = 6;
    public static final int WAKE_UP_REASON_UNKNOWN = 0;
    public static final int WAKE_UP_REASON_WAKEFULNESS_CHANGED = 7;
    public static final int WAKE_UP_REASON_WRAP_AROUND = 3;
    private ActivityManagerService mActivityService = null;
    private final ArrayList<ResolveInfo> mBrowserAppList = new ArrayList();
    int mExpandedHomeStatus = 0;
    final DualScreenPolicyHandler mHandler;
    boolean mNeedToUpdatePackageList = true;
    boolean mSingleScreenState = false;
    ActivityStackSupervisor mStackSupervisor = null;
    public boolean mTalkBackEnabled = false;

    private final class DualScreenPolicyHandler extends Handler {
        public DualScreenPolicyHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    synchronized (DualScreenPolicy.this.mActivityService) {
                        DualScreenPolicy.this.handleMultipleScreenStateChangedLocked(msg.arg1, msg.arg2);
                    }
                    return;
                default:
                    return;
            }
        }
    }

    public DualScreenPolicy(ActivityManagerService service) {
        this.mActivityService = service;
        this.mStackSupervisor = this.mActivityService.mStackSupervisor;
        this.mHandler = new DualScreenPolicyHandler(service.mHandler.getLooper());
    }

    public void onMultipleScreenStateChanged(int nextState, int reason) {
        Message msg = this.mHandler.obtainMessage(0);
        msg.arg1 = nextState;
        msg.arg2 = reason;
        this.mHandler.sendMessage(msg);
    }

    public void handleMultipleScreenStateChangedLocked(int nextState, int reason) {
        if (!this.mActivityService.mShuttingDown) {
            int currentScreenState = getScreenStateLocked();
            Slog.d("DualScreenPolicy", "currentScreenState=" + screenStateToString(currentScreenState) + ", nextState=" + screenStateToString(nextState) + ", reason=" + reason);
            if (this.mActivityService.mIsBackWindowShown && reason == 5) {
                if (nextState == 1 || nextState == 2) {
                    this.mActivityService.setBackWindowShownLocked(false, -1);
                }
                applyMultipleScreenState(nextState);
            } else if (currentScreenState != nextState) {
                applyMultipleScreenState(nextState);
                int prevBackWindowDisplayId = -1;
                boolean needToScreenStateChanged = false;
                if (this.mActivityService.mIsBackWindowShown) {
                    prevBackWindowDisplayId = this.mActivityService.mBackWindowDisplayId;
                    this.mActivityService.setBackWindowShownLocked(false, -1);
                    if (isActiveDisplayLocked(prevBackWindowDisplayId)) {
                        needToScreenStateChanged = true;
                    }
                }
                int expandedHomeStatus;
                ActivityRecord topActivityOnMain;
                ActivityRecord topActivityOnSub;
                switch (currentScreenState) {
                    case 0:
                        if (nextState != 1 && nextState != 2) {
                            if (nextState == 3) {
                                setSingleScreenStateLocked(false);
                                break;
                            }
                        }
                        setSingleScreenStateLocked(true);
                        moveExpandedHomeTaskToActiveScreenLocked(nextState == 1 ? 0 : 1);
                        break;
                        break;
                    case 1:
                        if (nextState != 2) {
                            if (nextState == 3) {
                                expandedHomeStatus = this.mExpandedHomeStatus;
                                topActivityOnMain = this.mStackSupervisor.topRunningActivityLocked();
                                topActivityOnSub = this.mStackSupervisor.topRunningActivityLocked();
                                if (!(topActivityOnMain == null || topActivityOnSub == null || topActivityOnMain.isRecentsActivity() || !topActivityOnSub.isRecentsActivity() || topActivityOnSub.task == null || topActivityOnSub.task.getStack() == null)) {
                                    setFullViewHomeStatusLocked(true, 1);
                                }
                                setSingleScreenStateLocked(false);
                                if (reason == 2 && expandedHomeStatus == 2) {
                                    this.mStackSupervisor.sendExpandRequestToExpandableActivityLocked(202);
                                    break;
                                }
                            }
                        }
                        moveExpandedHomeTaskToActiveScreenLocked(1);
                        break;
                        break;
                    case 2:
                        if (nextState != 1) {
                            if (nextState == 3) {
                                expandedHomeStatus = this.mExpandedHomeStatus;
                                topActivityOnMain = this.mStackSupervisor.topRunningActivityLocked();
                                topActivityOnSub = this.mStackSupervisor.topRunningActivityLocked();
                                if (!(topActivityOnMain == null || topActivityOnSub == null || !topActivityOnMain.isRecentsActivity() || topActivityOnSub.isRecentsActivity() || topActivityOnMain.task == null || topActivityOnMain.task.getStack() == null)) {
                                    setFullViewHomeStatusLocked(true, 0);
                                }
                                setSingleScreenStateLocked(false);
                                if (reason == 2 && expandedHomeStatus == 1) {
                                    this.mStackSupervisor.sendExpandRequestToExpandableActivityLocked(202);
                                    break;
                                }
                            }
                        }
                        moveExpandedHomeTaskToActiveScreenLocked(0);
                        break;
                        break;
                    case 3:
                        if (nextState == 1 || nextState == 2) {
                            setSingleScreenStateLocked(true);
                            int displayId = nextState == 1 ? 0 : 1;
                            if (!moveExpandedHomeTaskToActiveScreenLocked(displayId)) {
                                sendShrinkRequestIfNeededLocked(displayId);
                                break;
                            }
                        }
                        break;
                }
                if (needToScreenStateChanged) {
                    Slog.d("DualScreenPolicy", "needToScreenStateChanged due to BackWindow");
                    this.mActivityService.onScreenStateChanged(prevBackWindowDisplayId, 2);
                }
            }
        }
    }

    private void sendShrinkRequestIfNeededLocked(int displayId) {
        if (displayId == 0 || displayId == 1) {
            this.mStackSupervisor.sendShrinkRequestToAllResumedActivityLocked(ActivityStackSupervisor.convertScreenZoneToDisplayId(3 - ActivityStackSupervisor.convertDisplayIdToScreenZone(displayId)), 101);
            if (displayId == 1) {
                this.mActivityService.mWindowManager.moveInputMethodWindowsToDisplayIfNeededLocked(false);
            }
            this.mActivityService.mWindowManager.cancelDualScreenTransitionIfNeeded(DualScreen.displayIdToScreen(displayId));
        }
    }

    private int getScreenStateLocked() {
        int i;
        int i2 = 0;
        if (this.mActivityService.isScreenOn(0)) {
            i = 1;
        } else {
            i = 0;
        }
        int screenState = 0 | i;
        if (this.mActivityService.isScreenOn(1)) {
            i2 = 2;
        }
        return screenState | i2;
    }

    private void setSingleScreenStateLocked(boolean singleScreen) {
        if (this.mSingleScreenState != singleScreen) {
            Slog.d("DualScreenPolicy", "singleScreenState : " + this.mSingleScreenState + " => " + singleScreen);
            int displayId;
            if (singleScreen) {
                TaskRecord expandedHomeTask = this.mStackSupervisor.mExpandedHomeTask;
                if (expandedHomeTask != null) {
                    ActivityRecord topActivity = expandedHomeTask.stack.topRunningActivityLocked();
                    ActivityRecord expandedHomeActivity = expandedHomeTask.topRunningActivityLocked();
                    if (topActivity != null && topActivity == expandedHomeActivity) {
                        displayId = expandedHomeActivity.getDisplayId();
                        if (displayId >= 0 && displayId <= 2) {
                            this.mExpandedHomeStatus = FLAG_EXPANEDED_HOME_STATUS[displayId];
                        }
                    }
                }
            } else {
                displayId = 2;
                while (displayId >= 0 && !moveExpandedHomeTaskToActiveScreenLocked(displayId)) {
                    displayId--;
                }
                this.mExpandedHomeStatus = 0;
            }
            this.mSingleScreenState = singleScreen;
        }
    }

    private void applyMultipleScreenState(int state) {
        boolean mainScreenOn;
        boolean subScreenOn;
        int i;
        int i2 = 2;
        if ((state & 1) == 1) {
            mainScreenOn = true;
        } else {
            mainScreenOn = false;
        }
        if ((state & 2) == 2) {
            subScreenOn = true;
        } else {
            subScreenOn = false;
        }
        int[] iArr = this.mActivityService.mScreenState;
        if (mainScreenOn) {
            i = 2;
        } else {
            i = 1;
        }
        iArr[0] = i;
        int[] iArr2 = this.mActivityService.mScreenState;
        if (!subScreenOn) {
            i2 = 1;
        }
        iArr2[1] = i2;
    }

    public static String screenStateToString(int screenState) {
        switch (screenState) {
            case 0:
                return "ALL_SCREEN_OFF";
            case 1:
                return "MAIN_SCREEN_ON";
            case 2:
                return "SUB_SCREEN_ON";
            case 3:
                return "ALL_SCREEN_ON";
            default:
                return "UNKNOWN";
        }
    }

    public DualScreenAttrs applyDualScreenAttrs(ActivityRecord record, ProcessRecord caller, String launchedFromPackage, ActivityRecord resultTo, ActivityRecord sourceRecord, IBinder sourceToken) {
        return new DualScreenAttrs();
    }

    public static boolean isFullViewLaunchWithPriority(ActivityInfo aInfo) {
        return getBooleanMetaData(aInfo, "com.samsung.android.sdk.dualscreen.fullview.launchWithPriority");
    }

    public static boolean isSupportFullView(ActivityInfo aInfo) {
        return getBooleanMetaData(aInfo, "com.samsung.android.sdk.dualscreen.fullview.enable");
    }

    public static boolean getBooleanMetaData(ActivityInfo aInfo, String medataDataKey) {
        if (aInfo != null) {
            if (aInfo.metaData != null && aInfo.metaData.containsKey(medataDataKey)) {
                return aInfo.metaData.getBoolean(medataDataKey);
            }
            if (aInfo.applicationInfo.metaData != null && aInfo.applicationInfo.metaData.containsKey(medataDataKey)) {
                return aInfo.applicationInfo.metaData.getBoolean(medataDataKey);
            }
        }
        return false;
    }

    private boolean canBeLinkedApp(ActivityRecord r, ActivityRecord caller, ActivityRecord resultTo, String launchedFromPackage) {
        if (!this.mActivityService.isScreenOn(1)) {
            Log.d("DualScreenPolicy", "canBeLinkedApp Case : SUBSCREEN is off");
            return false;
        } else if (launchedFromPackage == null || caller == null) {
            Log.d("DualScreenPolicy", "canBeLinkedApp Case : From system(null)");
            return false;
        } else if (launchedFromPackage.equals("android") || launchedFromPackage.equals("com.android.systemui")) {
            Log.d("DualScreenPolicy", "canBeLinkedApp Case : From android or systemui");
            return false;
        } else if (launchedFromPackage.equals("com.google.android.setupwizard") || launchedFromPackage.equals("com.sec.android.app.SecSetupWizard")) {
            Log.d("DualScreenPolicy", "canBeLinkedApp Case : From setupwizard");
            return false;
        } else if (r.intent.hasCategory("android.intent.category.LAUNCHER")) {
            Log.d("DualScreenPolicy", "canBeLinkedApp Case : From Launcher");
            return false;
        } else if (caller.isHomeActivity() && !caller.isSamsungHomeActivity()) {
            Log.d("DualScreenPolicy", "canbeLinkedApp Case : called from HomeActivity");
            return false;
        } else if (resultTo != null) {
            Log.d("DualScreenPolicy", "canBeLinkedApp Case : exist resultTo");
            return false;
        } else if (r.info != null && (ResolverActivity.class.getName().equals(r.info.name) || ChooserActivity.class.getName().equals(r.info.name))) {
            Log.d("DualScreenPolicy", "canBeLinkedApp Case : Resolver(ChooserActivity)Activity");
            return false;
        } else if (this.mStackSupervisor.isInFixedScreenMode() && caller != null && DualScreenUtils.displayIdToScreen(caller.getDisplayId()) == DualScreen.MAIN) {
            Log.d("DualScreenPolicy", "canBeLinkedApp Case : In FixedScreenMode.");
            return false;
        } else if (isTalkBackEnabled()) {
            Log.d("DualScreenPolicy", "canBeLinkedApp Case : Voice Assistant Enabled.");
            return false;
        } else if (r.info == null || r.info.packageName.equals(launchedFromPackage) || "android.intent.action.MAIN".equals(r.intent.getAction()) || !isBrowserApp(r)) {
            Log.d("DualScreenPolicy", "canBeLinkedApp Case : else");
            return false;
        } else {
            Log.d("DualScreenPolicy", "canBeLinkedApp Case : true");
            return true;
        }
    }

    public void updateScreenForAllActivitiesInTask(TaskRecord task, DualScreen screen) {
        if (DEBUG) {
            Log.d("DualScreenPolicy", "updateScreenForAllActivitiesInTask() : screen=" + screen);
        }
    }

    public boolean resolveDisplayChooser(Intent intent, ActivityRecord caller, ResolveInfo rInfo) {
        if (DEBUG) {
            Log.d("DualScreenPolicy", "resolveDisplayChooser() : intent=" + intent);
            Log.d("DualScreenPolicy", "resolveDisplayChooser() : caller=" + caller);
            Log.d("DualScreenPolicy", "resolveDisplayChooser() : rInfo=" + rInfo);
            Log.d("DualScreenPolicy", "resolveDisplayChooser() : DualScreenFeatures.SUPPORT_DISPLAY_CHOOSER=" + DualScreenFeatures.SUPPORT_DISPLAY_CHOOSER);
            Log.d("DualScreenPolicy", "resolveDisplayChooser() : FocusedStack=" + this.mStackSupervisor.getFocusedStack());
        }
        if (intent.getAction() == "android.intent.action.MAIN" && DualScreenFeatures.SUPPORT_DISPLAY_CHOOSER) {
            if (!(caller == null || (intent.getFlags() & 268435456) == 0 || caller == null || !caller.isHomeActivity() || caller.dualScreenAttrs.getScreen() != DualScreen.MAIN)) {
                return true;
            }
        } else if (rInfo != null && DualScreenFeatures.SUPPORT_OPPOSITE_LAUNCH && intent.getAction() == "android.intent.action.VIEW" && !intent.getLaunchParams().fromDisplayChooser()) {
            String className = rInfo.activityInfo.name;
            String packageName = rInfo.activityInfo.packageName;
            if (caller != null) {
                DualScreenConfigs.getInstance();
                if (DualScreenConfigs.isOppositeLaunchSupportApp(caller.packageName) && !caller.packageName.equals(packageName)) {
                    intent.getLaunchParams().setFromOppositeLaunchApp(true);
                }
            }
            if (DualScreenFeatures.SUPPORT_DISPLAY_CHOOSER && intent.getLaunchParams().fromOppositeLaunchApp()) {
                if (!"android".equals(packageName)) {
                    return true;
                }
                //~ if (!(ResolverActivity.class.getName().equals(className) || ResolverGuideActivity.class.getName().equals(className))) {
                    //~ return true;
                //~ }
            }
        }
        return false;
    }

    public static TaskInfo makeTaskInfo(TaskRecord tr) {
        if (tr == null) {
            return null;
        }
        TaskInfo ti = new TaskInfo(tr.taskId);
        int taskType = tr.getType();
        if (taskType >= 3) {
            if (taskType == 3) {
                taskType = 0;
            } else if (taskType == 4) {
                taskType = 1;
            } else if (taskType == 6) {
                taskType = 6;
            }
        }
        ti.setTaskType(taskType);
        if (tr.stack != null) {
            ti.setScreen(DualScreenUtils.displayIdToScreen(tr.stack.getDisplayId()));
        } else {
            ti.setScreen(DualScreen.UNKNOWN);
        }
        ti.setFixed(tr.fixed);
        TaskRecord parentTask = tr.getParentCoupledTask();
        TaskRecord childTask = tr.getChildCoupledTask();
        if (parentTask != null) {
            ti.setParentCoupledTaskId(parentTask.taskId);
        }
        if (childTask != null) {
            ti.setChildCoupledTaskId(childTask.taskId);
        }
        ti.canMoveTaskToScreen = tr.canMoveTaskToScreen;
        return ti;
    }

    public static boolean canBeCoupled(ActivityRecord ar) {
        return true;
    }

    public static boolean isCoupled(ActivityRecord a, ActivityRecord b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.task != null) {
            TaskRecord targetParentTask = b.task.getParentCoupledTask();
            TaskRecord targetChildTask = b.task.getChildCoupledTask();
            if (targetParentTask != null && a.task.taskId == targetParentTask.taskId) {
                return true;
            }
            if (targetChildTask != null && a.task.taskId == targetChildTask.taskId) {
                return true;
            }
        }
        return false;
    }

    public static int getPolicyOrientation(int requestedOrientation, int targetActivityOrientation) {
        int orientation = targetActivityOrientation;
        switch (targetActivityOrientation) {
            case 0:
            case 6:
            case 8:
            case 11:
                if (requestedOrientation == 0 || requestedOrientation == 8 || requestedOrientation == 6 || requestedOrientation == 11) {
                    return requestedOrientation;
                }
                return orientation;
            case 1:
            case 7:
            case 9:
            case 12:
                if (requestedOrientation == 1 || requestedOrientation == 9 || requestedOrientation == 7 || requestedOrientation == 12) {
                    return requestedOrientation;
                }
                return orientation;
            default:
                return requestedOrientation;
        }
    }

    void setAppTokenDisplayIdLocked(ActivityRecord activity, int displayId) {
        this.mActivityService.mWindowManager.setAppTokenDisplayId(activity.appToken, displayId);
    }

    public void arrangeFullViewPolicyOnResumeTopActivitiesLocked(ActivityStack targetStack) {
        if (System.getIntForUser(this.mActivityService.mContext.getContentResolver(), "dual_screen_fullview_shrink_mode", 1, -2) != 0) {
            ArrayList<TaskRecord> tasks = this.mStackSupervisor.mUniversalTaskHistory;
            int N = tasks.size();
            if (N > 0) {
                TaskRecord topTask = null;
                TaskRecord nextTask = null;
                int screenZone = 0;
                for (int i = N - 1; i >= 0; i--) {
                    TaskRecord tr = (TaskRecord) tasks.get(i);
                    int taskScreenZone = tr.getScreenZone();
                    if (taskScreenZone != 0) {
                        if (topTask == null) {
                            topTask = tr;
                        }
                        screenZone |= taskScreenZone;
                        if (screenZone == 3) {
                            nextTask = tr;
                            break;
                        }
                    }
                }
                if (topTask != null && nextTask != null && topTask != nextTask && !topTask.isRecentTask() && nextTask.getScreenZone() == 3) {
                    int displayId = ActivityStackSupervisor.convertScreenZoneToDisplayId(3 - topTask.getScreenZone());
                    if (displayId != -1) {
                        this.mStackSupervisor.moveTaskToScreenLocked(nextTask, displayId, true, true, true, nextTask.isExpandHomeTask(), true);
                    }
                }
            }
        }
    }

    public int moveExpandHomeStackTaskToTopInner(int homeStackTaskType, String reason, int displayId, boolean preArrangeHomeTask) {
        return displayId;
    }

    public void setTalkBackEnabled(boolean talkBackEnabled) {
        synchronized (this.mActivityService) {
            if (this.mTalkBackEnabled != talkBackEnabled) {
                Slog.secD("DualScreenPolicy", "TalkBack " + (talkBackEnabled ? "enabled" : "disabled"));
                this.mTalkBackEnabled = talkBackEnabled;
            }
        }
    }

    public boolean isTalkBackEnabled() {
        return this.mTalkBackEnabled;
    }

    private boolean isBrowserApp(ActivityRecord r) {
        if (r.info == null) {
            return false;
        }
        if (this.mNeedToUpdatePackageList) {
            Intent queryIntent = new Intent("android.intent.action.MAIN");
            queryIntent.addCategory("android.intent.category.APP_BROWSER");
            try {
                List<ResolveInfo> list = AppGlobals.getPackageManager().queryIntentActivitiesInternal(queryIntent, null, 128, this.mActivityService.getCurrentUserIdLocked());
                this.mBrowserAppList.clear();
                this.mBrowserAppList.addAll(list);
                this.mNeedToUpdatePackageList = false;
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        Iterator i$ = this.mBrowserAppList.iterator();
        while (i$.hasNext()) {
            if (r.info.name.equals(((ResolveInfo) i$.next()).activityInfo.name)) {
                return true;
            }
        }
        return false;
    }

    protected void setFullViewHomeStatusLocked(ActivityRecord ar) {
        setFullViewHomeStatusLocked(ar.isExpandHomeActivity(), ar.getDisplayId());
    }

    protected void setFullViewHomeStatusLocked(boolean isExpandHomeActivity, int displayId) {
        if (!this.mSingleScreenState) {
            return;
        }
        if (isExpandHomeActivity) {
            this.mExpandedHomeStatus |= FLAG_EXPANEDED_HOME_STATUS[displayId];
        } else {
            this.mExpandedHomeStatus &= FLAG_EXPANEDED_HOME_STATUS[displayId] ^ -1;
        }
    }

    protected boolean isActiveDisplayLocked(int displayId) {
        int screenFlag;
        int screenState = getScreenStateLocked();
        switch (displayId) {
            case 0:
                screenFlag = 1;
                break;
            case 1:
                screenFlag = 2;
                break;
            case 2:
                screenFlag = 3;
                break;
            default:
                return true;
        }
        if ((screenState & screenFlag) != screenFlag) {
            return false;
        }
        return true;
    }

    protected boolean moveExpandedHomeTaskToActiveScreenLocked(int displayId) {
        if ((this.mExpandedHomeStatus & FLAG_EXPANEDED_HOME_STATUS[displayId]) != FLAG_EXPANEDED_HOME_STATUS[displayId]) {
            return false;
        }
        TaskRecord expandedHomeTask = this.mStackSupervisor.mExpandedHomeTask;
        if (expandedHomeTask == null) {
            return true;
        }
        /*ActivityRecord expandedHomeActivity = expandedHomeTask.topRunningActivityLocked(null);
        if (expandedHomeActivity == null || expandedHomeActivity.getDisplayId() == displayId) {
            return true;
        }*/
        this.mStackSupervisor.moveTaskToScreenLocked(expandedHomeTask, displayId, true, false, false);
        return true;
    }

    public void showBackWindowIfNeededLocked(ActivityRecord next) {
        if (this.mSingleScreenState && next != null && next.isApplicationActivity() && next.getDisplayId() != 2) {
            int targetDisplayId = ActivityStackSupervisor.convertScreenZoneToDisplayId(3 - ActivityStackSupervisor.convertDisplayIdToScreenZone(next.getDisplayId()));
            if (this.mActivityService.mScreenState[targetDisplayId] == 1) {
                this.mActivityService.setBackWindowShownLocked(true, targetDisplayId);
                this.mActivityService.mWindowManager.setAppBackWindow(targetDisplayId);
            }
        }
    }

    public void arrangeTaskToReturnTo() {
    }

    public int parseExpandedDisplayOrientation(String expandedDisplayOrienation) {
        if (expandedDisplayOrienation == null) {
            return -2;
        }
        if (expandedDisplayOrienation.equals("unspecified")) {
            return -1;
        }
        if (expandedDisplayOrienation.equals("behind")) {
            return 3;
        }
        if (expandedDisplayOrienation.equals("landscape")) {
            return 0;
        }
        if (expandedDisplayOrienation.equals("portrait")) {
            return 1;
        }
        if (expandedDisplayOrienation.equals("reverseLandscape")) {
            return 8;
        }
        if (expandedDisplayOrienation.equals("reversePortrait")) {
            return 9;
        }
        if (expandedDisplayOrienation.equals("sensorLandscape")) {
            return 6;
        }
        if (expandedDisplayOrienation.equals("sensorPortrait")) {
            return 7;
        }
        if (expandedDisplayOrienation.equals("userLandscape")) {
            return 11;
        }
        if (expandedDisplayOrienation.equals("userPortrait")) {
            return 12;
        }
        if (expandedDisplayOrienation.equals("sensor")) {
            return 4;
        }
        if (expandedDisplayOrienation.equals("fullSensor")) {
            return 10;
        }
        if (expandedDisplayOrienation.equals("nosensor")) {
            return 5;
        }
        if (expandedDisplayOrienation.equals("user")) {
            return 2;
        }
        if (expandedDisplayOrienation.equals("fullUser")) {
            return 13;
        }
        if (expandedDisplayOrienation.equals("locked")) {
            return 14;
        }
        return -2;
    }
}

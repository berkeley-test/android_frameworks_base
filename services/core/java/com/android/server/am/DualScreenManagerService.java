package com.android.server.am;

import android.app.AppGlobals;
import android.app.IApplicationThread;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.hardware.scontext.SContext;
//import android.hardware.scontext.SContextEvent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.provider.Settings.System;
import android.util.Slog;
import android.util.SparseArray;
import com.android.internal.os.TransferPipe;
import com.android.server.wm.WindowManagerService;
import com.samsung.android.dualscreen.DualScreen;
import com.samsung.android.dualscreen.DualScreenManager;
import com.samsung.android.dualscreen.TaskInfo;
import com.samsung.android.multidisplay.dualscreen.DualScreenFeatures;
import com.samsung.android.multidisplay.dualscreen.DualScreenSettings;
import com.samsung.android.multidisplay.dualscreen.DualScreenSettings.OnSettingChangedListener;
import com.samsung.android.multidisplay.dualscreen.DualScreenUtils;
import com.samsung.android.multidisplay.dualscreen.IDualScreenCallbacks;
import com.samsung.android.multidisplay.dualscreen.IDualScreenManager.Stub;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DualScreenManagerService extends Stub implements OnSettingChangedListener {
    private static final boolean DEBUG = DualScreenManager.DEBUG;
    private static final int FOCUS_SCREEN_MSG = 1004;
    private static final int MOVE_TASK_TO_SCREEN_MSG = 1001;
    private static final int REPORT_SCONTEXT_HALL_SENSOR_CHANGE_MSG = 1006;
    private static final int REPORT_SCONTEXT_MAIN_SCREEN_DETECTION_CHANGE_MSG = 1007;
    private static final int REPORT_SCREEN_FOCUS_CHANGE_MSG = 1003;
    private static final int REPORT_SHRINK_REQUEST_STATE_MSG = 1005;
    private static final int SWAP_TOP_TASK_MSG = 1002;
    private static final String TAG = DualScreenManagerService.class.getSimpleName();
    private static DualScreenManagerService sSelf = null;
    private final ActivityManagerService mActivityManager;
    private Context mContext;
    private RemoteCallbackList<IDualScreenCallbacks> mDualScreenCallbacks;
    private DualScreenSettings mDualScreenSettings;
    private DualScreen mFocusedScreen;
    private Handler mHandler = new MyHandler(this.mActivityManager.mHandler.getLooper());
    private ActivityStackSupervisor mStackSupervisor;
    private WindowManagerService mWindowManager;

    /* renamed from: com.android.server.am.DualScreenManagerService$1TransferPipeThread */
    class AnonymousClass1TransferPipeThread extends Thread {
        final String[] args;
        final ParcelFileDescriptor fd;
        final IApplicationThread thread;
        final /* synthetic */ String val$innerPrefix;

        public AnonymousClass1TransferPipeThread(IApplicationThread _thread, ParcelFileDescriptor _fd, String[] _args, String str) {
            super("TransferPipeWrite");
            this.val$innerPrefix = str;
            this.thread = _thread;
            this.fd = _fd;
            this.args = _args;
        }

        public void run() {
            try {
                this.thread.dumpContextRelationInfo(this.fd, this.val$innerPrefix, this.args);
            } catch (RemoteException e) {
            }
        }
    }

    private class MyHandler extends Handler {
        public MyHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1001:
                    Slog.d(DualScreenManagerService.TAG, "handleMessage() : MOVE_TASK_TO_FRONT_MSG");
                    int taskId = msg.arg1;
                    int flags = msg.arg2;
                    Bundle options = null;
                    DualScreen toScreen = null;
                    if (msg.obj != null) {
                        Bundle bundle = msg.obj;
                        toScreen = (DualScreen) bundle.getParcelable("dualscreen:displayid");
                        options = (Bundle) bundle.getParcelable("dualscreen:activityoption");
                    }
                    DualScreenManagerService.this.handleMoveTaskToScreen(taskId, flags, options, toScreen);
                    return;
                case 1002:
                    Slog.d(DualScreenManagerService.TAG, "handleMessage() : SWAP_TOP_TASK_MSG");
                    DualScreenManagerService.this.handleSwapTopTask();
                    return;
                case 1003:
                    Slog.d(DualScreenManagerService.TAG, "handleMessage() : REPORT_SCREEN_FOCUS_CHANGE_MSG");
                    DualScreenManagerService.this.handleReportScreenFocusChanged(msg.obj);
                    return;
                case 1004:
                    Slog.d(DualScreenManagerService.TAG, "handleMessage() : FOCUS_SCREEN_MSG");
                    DualScreenManagerService.this.handleFocusScreen((DualScreen) msg.obj);
                    return;
                case 1005:
                    Slog.d(DualScreenManagerService.TAG, "handleMessage() : REPORT_SHRINK_REQUEST_STATE_MSG");
                    DualScreenManagerService.this.handleReportShrinkRequestedState(((Boolean) msg.obj).booleanValue());
                    return;
                case 1006:
                    Slog.d(DualScreenManagerService.TAG, "handleMessage() : REPORT_SCONTEXT_HALL_SENSOR_CHANGE_MSG");
                    DualScreenManagerService.this.handleReportSContextStatusChange(msg.obj);
                    return;
                case 1007:
                    Slog.d(DualScreenManagerService.TAG, "handleMessage() : REPORT_SCONTEXT_MAIN_SCREEN_DETECTION_CHANGE_MSG");
                    DualScreenManagerService.this.handleReportSContextStatusChange(msg.obj);
                    return;
                default:
                    return;
            }
        }
    }

    public DualScreenManagerService(Context context, ActivityManagerService service) {
        this.mContext = context;
        this.mActivityManager = service;
        this.mStackSupervisor = service.mStackSupervisor;
        sSelf = this;
        this.mDualScreenCallbacks = new RemoteCallbackList<IDualScreenCallbacks>() {
            public void onCallbackDied(IDualScreenCallbacks callback) {
                super.onCallbackDied(callback);
                unregister(callback);
            }
        };
    }

    public boolean canBeCoupled(IBinder token) {
        return false;
    }

    public boolean canBeExpanded(int taskId) {
        return false;
    }

    public DualScreen getFocusedScreen() {
        return DualScreen.UNKNOWN;
    }

    public int getOrientation(DualScreen screen) {
        return 0;
    }

    public DualScreen getScreen(int taskId) {
        return DualScreen.UNKNOWN;
    }

    public TaskInfo getTaskInfo(int taskId) {
        return null;
    }

    public List<TaskInfo> getTasks(int maxNum, int flags, DualScreen screen) {
        return null;
    }

    public int getTopRunningTaskIdWithPermission(DualScreen screen) {
        return -1;
    }

    public TaskInfo getTopRunningTaskInfo(DualScreen screen) {
        return null;
    }

    public void moveTaskToScreen(IBinder token, DualScreen toScreen) {
    }

    public void moveTaskToScreenWithPermission(int taskId, DualScreen toScreen, int flags, Bundle options) {
    }

    public void fixTask(int taskId) {
    }

    public void unfixTask(int taskId) {
    }

    public void fixTopTask(DualScreen screen) {
    }

    public void focusScreen(IBinder token, DualScreen screen) {
    }

    public void forceFocusScreen(DualScreen screen) {
    }

    public void unfixTopTask(DualScreen screen) {
    }

    public boolean isExpandable(int taskId) {
        return false;
    }

    public boolean isInFixedScreenMode(DualScreen screen) {
        return false;
    }

    public void registerDualScreenCallbacks(IDualScreenCallbacks callback) {
    }

    public void registerExpandableActivity(IBinder token) {
    }

    public void dimScreen(IBinder token, DualScreen screen, boolean enable) {
    }

    public void overrideNextAppTransition(IBinder token, DualScreen screen, int transit) {
    }

    public void requestOppositeDisplayOrientation(IBinder token, int requestedOrientation) {
    }

    public void requestExpandedDisplayOrientation(IBinder token, int requestedOrientation) {
    }

    public void setExpandable(IBinder token, boolean expandable) {
    }

    public void sendExpandRequest(int targetTaskId) {
        if (DEBUG) {
            Slog.d(TAG, "sendExpandRequest() taskId=" + targetTaskId);
        }
        synchronized (this.mActivityManager) {
            TaskRecord task = this.mStackSupervisor.anyTaskForIdLocked(targetTaskId);
            if (task == null) {
                String msg = "Invalid task id=" + targetTaskId + " : sendExpandRequest() from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid();
                Slog.w(TAG, msg);
                throw new IllegalArgumentException(msg);
            }
            ActivityRecord topActivity = task.topRunningActivityLocked();
            if (!(topActivity == null || !topActivity.isExpandable || topActivity.task == null || topActivity.task.stack == null)) {
                topActivity.task.stack.sendExpandRequestToActivityLocked(topActivity, 201);
            }
        }
    }

    public void sendShrinkRequest(int targetTaskId, DualScreen toScreen) {
        if (DEBUG) {
            Slog.d(TAG, "sendShrinkRequest() taskId=" + targetTaskId + " toScreen=" + toScreen);
        }
        String msg;
        if (toScreen == null) {
            msg = "Invalid parameter. toScreen=" + toScreen + " : sendShrinkRequest() from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid();
            Slog.w(TAG, msg);
            throw new IllegalArgumentException(msg);
        }
        synchronized (this.mActivityManager) {
            TaskRecord task = this.mStackSupervisor.anyTaskForIdLocked(targetTaskId);
            if (task == null) {
                msg = "Invalid task id=" + targetTaskId + " : sendShrinkRequest() from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid();
                Slog.w(TAG, msg);
                throw new IllegalArgumentException(msg);
            }
            ActivityRecord topActivity = task.topRunningActivityLocked();
            if (!(topActivity == null || ((!topActivity.isCoupled() && topActivity.getScreen() != DualScreen.FULL) || topActivity.task == null || topActivity.task.stack == null))) {
                topActivity.task.stack.sendShrinkRequestToActivityLocked(topActivity, toScreen.getDisplayId(), 103);
            }
        }
    }

    public void setFinishWithCoupledTask(IBinder token, boolean finish) {
        if (DEBUG) {
            Slog.d(TAG, "setFinishWithCoupledTask() finish=" + finish);
        }
        synchronized (this.mActivityManager) {
            ActivityRecord r = ActivityRecord.isInStackLocked(token);
            if (r == null) {
                String msg = "Invalid token: setFinishWithCoupledTask(token=" + token + ", finish) from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid();
                Slog.w(TAG, msg);
                throw new IllegalArgumentException(msg);
            }
            if (r != null) {
                if (r.task != null) {
                    r.task.setFinishWithCoupledTask(finish);
                    if (r.task.getCoupledTask() != null) {
                        r.task.getCoupledTask().setFinishWithCoupledTask(finish);
                    }
                }
            }
        }
    }

    public boolean isTiggerActivity(ActivityRecord sourceActivity, ActivityRecord targetActivity) {
        if (sourceActivity == null || targetActivity == null) {
            if (!DEBUG) {
                return false;
            }
            Slog.d(TAG, "isTiggerActivity() param is not correct sourceActivity=" + sourceActivity + " targetActivity=" + targetActivity);
            return false;
        } else if (targetActivity.sourceActivity == null || targetActivity.sourceActivity.getClassName() == null || !targetActivity.sourceActivity.getClassName().equals("com.android.internal.app.ResolverActivity") || targetActivity.dualScreenAttrs.triggerActivity != sourceActivity.realActivity) {
            return false;
        } else {
            return true;
        }
    }

    public void finishCoupledActivity(IBinder token, int flags) {
        if (DEBUG) {
            Slog.d(TAG, "finishCoupledActivity() flags=" + flags);
        }
        synchronized (this.mActivityManager) {
            long origId = Binder.clearCallingIdentity();
            try {
                ActivityRecord r = ActivityRecord.isInStackLocked(token);
                String msg;
                if (r == null) {
                    msg = "Invalid token: finishCoupledActivity(token=" + token + ", flags) from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid();
                    Slog.w(TAG, msg);
                    throw new IllegalArgumentException(msg);
                }
                if (r != null) {
                    if (r.task != null && r.task.getCoupledTask() != null) {
                        boolean isClearAllAbove = (flags & 4096) != 0;
                        boolean isClearExceptTop = (flags & 8192) != 0;
                        TaskRecord OppositeTask = r.task.getCoupledTask();
                        int activityNdx;
                        ActivityRecord ar;
                        if (isClearAllAbove && isClearExceptTop) {
                            msg = "Invalid flags: finishOppositeCoupled(flags=" + flags + ") from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid();
                            Slog.w(TAG, msg);
                            throw new IllegalArgumentException(msg);
                        } else if ((flags & 256) != 0) {
                            OppositeTask.removeTaskActivitiesLocked();
                        } else if ((flags & 512) != 0) {
                            if (isClearAllAbove) {
                                for (activityNdx = 0; activityNdx < OppositeTask.mActivities.size(); activityNdx++) {
                                    ar = (ActivityRecord) OppositeTask.mActivities.get(activityNdx);
                                    if (!ar.finishing && ((ar.sourceActivity == r.realActivity || isTiggerActivity(r, ar)) && OppositeTask.stack != null)) {
                                        OppositeTask.performClearTaskAtIndexLocked(activityNdx, "finish callee and above all");
                                        break;
                                    }
                                }
                            } else {
                                int startNdx;
                                if (isClearExceptTop) {
                                    startNdx = OppositeTask.mActivities.size() - 2;
                                } else {
                                    startNdx = OppositeTask.mActivities.size() - 1;
                                }
                                for (activityNdx = startNdx; activityNdx >= 0; activityNdx--) {
                                    ar = (ActivityRecord) OppositeTask.mActivities.get(activityNdx);
                                    if (!ar.finishing && ((ar.sourceActivity == r.realActivity || isTiggerActivity(r, ar)) && OppositeTask.stack != null)) {
                                        OppositeTask.stack.finishActivityLocked(ar, 0, null, "finish-coupled", false);
                                    }
                                }
                            }
                        } else if ((flags & 1024) != 0) {
                            if (!isClearAllAbove) {
                                for (activityNdx = OppositeTask.mActivities.size() - 1; activityNdx >= 0; activityNdx--) {
                                    ar = (ActivityRecord) OppositeTask.mActivities.get(activityNdx);
                                    if (!ar.finishing && ((ar.realActivity == r.sourceActivity || isTiggerActivity(ar, r)) && OppositeTask.stack != null)) {
                                        OppositeTask.stack.finishActivityLocked(ar, 0, null, "finish-coupled", false);
                                        break;
                                    }
                                }
                            } else {
                                for (activityNdx = 0; activityNdx < OppositeTask.mActivities.size(); activityNdx++) {
                                    ar = (ActivityRecord) OppositeTask.mActivities.get(activityNdx);
                                    if (!ar.finishing && ((ar.realActivity == r.sourceActivity || isTiggerActivity(ar, r)) && OppositeTask.stack != null)) {
                                        OppositeTask.performClearTaskAtIndexLocked(activityNdx, "finish caller and above all");
                                        break;
                                    }
                                }
                            }
                        } else if ((flags & 2048) != 0) {
                            int TopActivityNdx = OppositeTask.mActivities.size() - 1;
                            if (TopActivityNdx >= 0) {
                                ar = (ActivityRecord) OppositeTask.mActivities.get(TopActivityNdx);
                                if (ar != null) {
                                    OppositeTask.stack.finishActivityLocked(ar, 0, null, "finish-coupled", false);
                                }
                            }
                        } else {
                            Slog.e(TAG, "finishOppositeCoupled() : flags is not correct");
                        }
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(origId);
            }
        }
    }

    public void swapTopTask() {
    }

    public void unregisterDualScreenCallbacks(IDualScreenCallbacks callback) {
    }

    public void unregisterExpandableActivity(IBinder token) {
    }

    /* JADX WARNING: Missing block: B:71:?, code skipped:
            return;
     */
    void handleMoveTaskToScreen(int r19, int r20, android.os.Bundle r21, com.samsung.android.dualscreen.DualScreen r22) {
        /*
        r18 = this;
        r4 = DEBUG;
        if (r4 == 0) goto L_0x0031;
    L_0x0004:
        r4 = TAG;
        r6 = new java.lang.StringBuilder;
        r6.<init>();
        r7 = "handleMoveTaskToScreen(taskId=";
        r6 = r6.append(r7);
        r0 = r19;
        r6 = r6.append(r0);
        r7 = ", toScreen=";
        r6 = r6.append(r7);
        r0 = r22;
        r6 = r6.append(r0);
        r7 = ")";
        r6 = r6.append(r7);
        r6 = r6.toString();
        android.util.Slog.d(r4, r6);
    L_0x0031:
        if (r19 <= 0) goto L_0x0035;
    L_0x0033:
        if (r22 != 0) goto L_0x0036;
    L_0x0035:
        return;
    L_0x0036:
        r0 = r18;
        r0 = r0.mActivityManager;
        r17 = r0;
        monitor-enter(r17);
        r0 = r18;
        r4 = r0.mStackSupervisor;	 Catch:{ all -> 0x005d }
        r0 = r19;
        r5 = r4.anyTaskForIdLocked(r0);	 Catch:{ all -> 0x005d }
        r16 = 0;
        r12 = 0;
        if (r5 == 0) goto L_0x0074;
    L_0x004c:
        r4 = 0;
        r16 = r5.topRunningActivityLocked(r4);	 Catch:{ all -> 0x005d }
        if (r16 != 0) goto L_0x0060;
    L_0x0053:
        r4 = TAG;	 Catch:{ all -> 0x005d }
        r6 = "top task is null";
        android.util.Slog.w(r4, r6);	 Catch:{ all -> 0x005d }
        monitor-exit(r17);	 Catch:{ all -> 0x005d }
        goto L_0x0035;
    L_0x005d:
        r4 = move-exception;
        monitor-exit(r17);	 Catch:{ all -> 0x005d }
        throw r4;
    L_0x0060:
        r4 = com.samsung.android.multidisplay.dualscreen.DualScreenSettings.isExpandHomeModeEnabled();	 Catch:{ all -> 0x005d }
        if (r4 == 0) goto L_0x00ca;
    L_0x0066:
        r4 = r16.isExpandHomeActivity();	 Catch:{ all -> 0x005d }
        if (r4 == 0) goto L_0x00ca;
    L_0x006c:
        r4 = TAG;	 Catch:{ all -> 0x005d }
        r6 = "allow to move Expand Home to all displays";
        android.util.Slog.secD(r4, r6);	 Catch:{ all -> 0x005d }
        r12 = 1;
    L_0x0074:
        r13 = 0;
        if (r21 == 0) goto L_0x0080;
    L_0x0077:
        r4 = "dualscreen:noanim";
        r0 = r21;
        r13 = r0.getBoolean(r4);	 Catch:{ all -> 0x005d }
    L_0x0080:
        if (r16 == 0) goto L_0x0088;
    L_0x0082:
        r4 = r16.isApplicationActivity();	 Catch:{ all -> 0x005d }
        if (r4 != 0) goto L_0x008a;
    L_0x0088:
        if (r12 == 0) goto L_0x013f;
    L_0x008a:
        r0 = r16;
        r4 = r0.dualScreenAttrs;	 Catch:{ all -> 0x005d }
        r10 = r4.getScreen();	 Catch:{ all -> 0x005d }
        r11 = 0;
        r0 = r22;
        if (r10 != r0) goto L_0x0099;
    L_0x0097:
        if (r11 == 0) goto L_0x0133;
    L_0x0099:
        r0 = r18;
        r1 = r16;
        r4 = r0.canMoveToScreenLocked(r1);	 Catch:{ all -> 0x005d }
        if (r4 == 0) goto L_0x0125;
    L_0x00a3:
        r14 = android.os.Binder.clearCallingIdentity();	 Catch:{ all -> 0x005d }
        r0 = r16;
        r4 = r0.nowVisible;	 Catch:{ all -> 0x0104 }
        if (r4 == 0) goto L_0x0109;
    L_0x00ad:
        if (r13 == 0) goto L_0x00f5;
    L_0x00af:
        r0 = r18;
        r4 = r0.mStackSupervisor;	 Catch:{ all -> 0x0104 }
        r6 = r22.getDisplayId();	 Catch:{ all -> 0x0104 }
        r7 = 1;
        r8 = 1;
        r9 = 0;
        r4.moveTaskToScreenLocked(r5, r6, r7, r8, r9);	 Catch:{ all -> 0x0104 }
    L_0x00bd:
        r0 = r18;
        r1 = r22;
        r0.handleFocusScreen(r1);	 Catch:{ all -> 0x0104 }
        android.os.Binder.restoreCallingIdentity(r14);	 Catch:{ all -> 0x005d }
    L_0x00c7:
        monitor-exit(r17);	 Catch:{ all -> 0x005d }
        goto L_0x0035;
    L_0x00ca:
        r4 = r16.isSamsungHomeActivity();	 Catch:{ all -> 0x005d }
        if (r4 == 0) goto L_0x00de;
    L_0x00d0:
        r4 = r16.getScreen();	 Catch:{ all -> 0x005d }
        r6 = com.samsung.android.dualscreen.DualScreen.SUB;	 Catch:{ all -> 0x005d }
        if (r4 != r6) goto L_0x00de;
    L_0x00d8:
        r4 = com.samsung.android.dualscreen.DualScreen.FULL;	 Catch:{ all -> 0x005d }
        r0 = r22;
        if (r0 == r4) goto L_0x00ec;
    L_0x00de:
        r4 = r16.getScreen();	 Catch:{ all -> 0x005d }
        r6 = com.samsung.android.dualscreen.DualScreen.FULL;	 Catch:{ all -> 0x005d }
        if (r4 != r6) goto L_0x0074;
    L_0x00e6:
        r4 = com.samsung.android.dualscreen.DualScreen.SUB;	 Catch:{ all -> 0x005d }
        r0 = r22;
        if (r0 != r4) goto L_0x0074;
    L_0x00ec:
        r4 = TAG;	 Catch:{ all -> 0x005d }
        r6 = "allow to move SubHomeTask between SUB and EXPANDED";
        android.util.Slog.secD(r4, r6);	 Catch:{ all -> 0x005d }
        r12 = 1;
        goto L_0x0074;
    L_0x00f5:
        r0 = r18;
        r4 = r0.mStackSupervisor;	 Catch:{ all -> 0x0104 }
        r6 = r22.getDisplayId();	 Catch:{ all -> 0x0104 }
        r7 = 1;
        r8 = 1;
        r9 = 1;
        r4.moveTaskToScreenLocked(r5, r6, r7, r8, r9);	 Catch:{ all -> 0x0104 }
        goto L_0x00bd;
    L_0x0104:
        r4 = move-exception;
        android.os.Binder.restoreCallingIdentity(r14);	 Catch:{ all -> 0x005d }
        throw r4;	 Catch:{ all -> 0x005d }
    L_0x0109:
        r0 = r18;
        r4 = r0.mStackSupervisor;	 Catch:{ all -> 0x0104 }
        r6 = r22.getDisplayId();	 Catch:{ all -> 0x0104 }
        r7 = 1;
        r8 = 0;
        r9 = 0;
        r4.moveTaskToScreenLocked(r5, r6, r7, r8, r9);	 Catch:{ all -> 0x0104 }
        r0 = r18;
        r4 = r0.mActivityManager;	 Catch:{ all -> 0x0104 }
        r0 = r19;
        r1 = r20;
        r2 = r21;
        r4.moveTaskToFront(r0, r1, r2);	 Catch:{ all -> 0x0104 }
        goto L_0x00bd;
    L_0x0125:
        r0 = r18;
        r4 = r0.mStackSupervisor;	 Catch:{ all -> 0x005d }
        r4 = r4.mPendingActivitiesToMove;	 Catch:{ all -> 0x005d }
        r0 = r16;
        r1 = r22;
        r4.put(r0, r1);	 Catch:{ all -> 0x005d }
        goto L_0x00c7;
    L_0x0133:
        r0 = r18;
        r4 = r0.mStackSupervisor;	 Catch:{ all -> 0x005d }
        r4 = r4.mPendingActivitiesToMove;	 Catch:{ all -> 0x005d }
        r0 = r16;
        r4.remove(r0);	 Catch:{ all -> 0x005d }
        goto L_0x00c7;
    L_0x013f:
        r4 = TAG;	 Catch:{ all -> 0x005d }
        r6 = new java.lang.StringBuilder;	 Catch:{ all -> 0x005d }
        r6.<init>();	 Catch:{ all -> 0x005d }
        r7 = "moveTaskToScreen() : cannot move task=";
        r6 = r6.append(r7);	 Catch:{ all -> 0x005d }
        r6 = r6.append(r5);	 Catch:{ all -> 0x005d }
        r6 = r6.toString();	 Catch:{ all -> 0x005d }
        android.util.Slog.w(r4, r6);	 Catch:{ all -> 0x005d }
        monitor-exit(r17);	 Catch:{ all -> 0x005d }
        goto L_0x0035;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.server.am.DualScreenManagerService.handleMoveTaskToScreen(int, int, android.os.Bundle, com.samsung.android.dualscreen.DualScreen):void");
    }

    public void onChange(String setting) {
        if (DEBUG) {
            Slog.d(TAG, "onChange() : " + setting);
        }
        if (!"dual_screen_mode_enabled".equals(setting) && !"desktop_mode_enabled".equals(setting)) {
            if ("dual_screen_display_chooser_enabled".equals(setting)) {
                DualScreenFeatures.SUPPORT_DISPLAY_CHOOSER = this.mDualScreenSettings.isDualScreenDisplayChooserEnabled();
                if (DEBUG) {
                    Slog.d(TAG, "onChange() : DualScreenFeatures.SUPPORT_DISPLAY_CHOOSER=" + DualScreenFeatures.SUPPORT_DISPLAY_CHOOSER);
                }
            } else if ("dual_screen_opposite_launch_enabled".equals(setting)) {
                DualScreenFeatures.SUPPORT_OPPOSITE_LAUNCH = this.mDualScreenSettings.isDualScreenOppositeLaunchEnabled();
                if (DEBUG) {
                    Slog.d(TAG, "onChange() : DualScreenFeatures.SUPPORT_OPPOSITE_LAUNCH=" + DualScreenFeatures.SUPPORT_OPPOSITE_LAUNCH);
                }
            } else if (!"dualscreen_prototype".equals(setting)) {
                if (DualScreenFeatures.SUPPORT_PINNED_HOME && "subhome_package_info".equals(setting)) {
                    String componentName = System.getStringForUser(this.mContext.getContentResolver(), "subhome_package_info", -2);
                    if (!(componentName == null || "".equals(componentName))) {
                        String[] splits = componentName.split("/");
                        if (!(splits.length != 2 || "".equals(splits[0]) || "".equals(splits[1]))) {
                            ComponentName cn = new ComponentName(splits[0], splits[1]);
                            ActivityInfo ai = null;
                            try {
                                ai = AppGlobals.getPackageManager().getActivityInfo(cn, 1024, this.mActivityManager.mCurrentUserId);
                            } catch (RemoteException e) {
                            }
                            if (ai != null) {
                                this.mActivityManager.setSamsungHomeComponentName(cn);
                                return;
                            }
                        }
                    }
                    this.mActivityManager.setSamsungHomeComponentName(null);
                } else if ("enabled_accessibility_services".equals(setting)) {
                    this.mActivityManager.mDualScreenPolicy.setTalkBackEnabled(this.mDualScreenSettings.isTalkBackEnabled());
                } else if ("launcher_fullview_mode".equals(setting)) {
                    synchronized (this.mActivityManager) {
                        Iterator i$;
                        if (DualScreenSettings.isExpandHomeModeEnabled()) {
                            TaskRecord homeTask = getCandidateExpandedHomeTask();
                            if (homeTask != null) {
                                homeTask.taskType = 6;
                                i$ = homeTask.mActivities.iterator();
                                while (i$.hasNext()) {
                                    ((ActivityRecord) i$.next()).mActivityType = 6;
                                }
                                this.mStackSupervisor.moveTaskToScreenLocked(homeTask, 2, true, true, true);
                                this.mStackSupervisor.mExpandedHomeTask = homeTask;
                            }
                        } else {
                            TaskRecord expandedHomeTask = this.mActivityManager.mStackSupervisor.mExpandedHomeTask;
                            if (expandedHomeTask != null) {
                                expandedHomeTask.taskType = 1;
                                i$ = expandedHomeTask.mActivities.iterator();
                                while (i$.hasNext()) {
                                    ((ActivityRecord) i$.next()).mActivityType = 1;
                                }
                                this.mStackSupervisor.moveTaskToScreenLocked(expandedHomeTask, 0, true, true, true);
                                this.mStackSupervisor.mExpandedHomeTask = null;
                                this.mActivityManager.startHomeActivityLocked(this.mActivityManager.mCurrentUserId, "Launcher-SingleView", 1);
                            }
                        }
                    }
                }
            }
        }
    }

    private TaskRecord getCandidateExpandedHomeTask() {
        ActivityStack homeStack = this.mStackSupervisor.getHomeStack();
        if (homeStack == null) {
            return null;
        }
        ArrayList<TaskRecord> tasks = homeStack.getAllTasks();
        for (int taskNdx = tasks.size() - 1; taskNdx >= 0; taskNdx--) {
            TaskRecord task = (TaskRecord) tasks.get(taskNdx);
            if (task.isHomeTask()) {
                ActivityRecord homeActivity = task.topRunningActivityLocked();
                if (homeActivity != null) {
                    DualScreenPolicy dualScreenPolicy = this.mActivityManager.mDualScreenPolicy;
                    if (DualScreenPolicy.isFullViewLaunchWithPriority(homeActivity.info)) {
                        return task;
                    }
                } else {
                    continue;
                }
            }
        }
        return null;
    }

    public void reportScreenFocusChanged(int displayId) {
        if (DEBUG) {
            Slog.d(TAG, "reportScreenFocusChanged() : displayId=" + displayId);
        }
        DualScreen focusedScreen = DualScreenUtils.displayIdToScreen(displayId);
        if (focusedScreen == DualScreen.UNKNOWN) {
            Slog.e(TAG, "reportScreenFocusChanged() : unknown screen");
            return;
        }
        this.mHandler.removeMessages(1003);
        this.mHandler.sendMessage(this.mHandler.obtainMessage(1003, focusedScreen));
    }

    public void reportShrinkRequestState(boolean shrinkRequested) {
        if (DEBUG) {
            Slog.d(TAG, "reportShrinkRequestState() : shrinkRequested=" + shrinkRequested);
        }
        this.mHandler.removeMessages(1005);
        this.mHandler.sendMessage(this.mHandler.obtainMessage(1005, Boolean.valueOf(shrinkRequested)));
    }

    public void reportSContextChange(SContext event) {
        if (DEBUG) {
            Slog.d(TAG, "reportSCotextChange() : event=" + event);
        }
        if (event != null) {
            int what;
            SContext scontext = event.scontext;
            if (scontext.getType() == 43) {
                what = 1006;
            } else if (scontext.getType() == 49) {
                what = 1007;
            } else {
                return;
            }
            this.mHandler.removeMessages(what);
            this.mHandler.sendMessage(this.mHandler.obtainMessage(what, event));
        }
    }

    void registerBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.DUALSCREEN_TEST_SCREENOFF");
        intentFilter.addAction("android.intent.action.DUALSCREEN_TEST_SCREENON");
        this.mContext.registerReceiver(new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                if (DualScreenManagerService.DEBUG) {
                    Slog.v(DualScreenManagerService.TAG, "onReceive() action=" + intent.getAction());
                }
                if ("android.intent.action.DUALSCREEN_TEST_SCREENOFF".equals(intent.getAction())) {
                    DualScreenManagerService.this.reportShrinkRequestState(true);
                } else if ("android.intent.action.DUALSCREEN_TEST_SCREENON".equals(intent.getAction())) {
                    DualScreenManagerService.this.reportShrinkRequestState(false);
                }
            }
        }, intentFilter);
    }

    public static DualScreenManagerService self() {
        return sSelf;
    }

    void setWindowManager(WindowManagerService wm) {
        synchronized (this.mActivityManager) {
            this.mWindowManager = wm;
        }
    }

    void systemReady() {
        this.mDualScreenSettings = new DualScreenSettings(this.mContext);
        this.mDualScreenSettings.setOnSettingChangedListener(this);
        this.mDualScreenSettings.init();
        DualScreenFeatures.SUPPORT_DISPLAY_CHOOSER = this.mDualScreenSettings.isDualScreenDisplayChooserEnabled();
        Slog.d(TAG, "systemReady() : DualScreenFeatures.SUPPORT_DISPLAY_CHOOSER=" + DualScreenFeatures.SUPPORT_DISPLAY_CHOOSER);
        DualScreenFeatures.SUPPORT_OPPOSITE_LAUNCH = this.mDualScreenSettings.isDualScreenOppositeLaunchEnabled();
        Slog.d(TAG, "systemReady() : DualScreenFeatures.SUPPORT_OPPOSITE_LAUNCH=" + DualScreenFeatures.SUPPORT_OPPOSITE_LAUNCH);
        DualScreenFeatures.SUPPORT_DESKTOP_MODE = this.mDualScreenSettings.isDesktopModeEnabled();
        Slog.d(TAG, "systemReady() : DualScreenFeatures.SUPPORT_DESKTOP_MODE=" + DualScreenFeatures.SUPPORT_DESKTOP_MODE);
    }

    private boolean canSwapScreenLocked(ActivityRecord r) {
        if (r.isApplicationActivity() && r.state == ActivityState.RESUMED) {
            return true;
        }
        return false;
    }

    private boolean canMoveToScreenLocked(ActivityRecord r) {
        return true;
    }

    private void fixTaskLocked(int taskId) {
    }

    private int getTopRunningTaskId(DualScreen screen) {
        return -1;
    }

    private void handleFocusScreen(DualScreen screen) {
        if (DEBUG) {
            Slog.d(TAG, "handleFocusScreen() : " + screen);
        }
        if (screen != null) {
            synchronized (this.mActivityManager) {
                ArrayList<ActivityStack> mStacks = this.mStackSupervisor.getStacks(screen);
                int stackSize = mStacks.size();
                if (stackSize > 0) {
                    this.mWindowManager.cancelTapOupStackMsg();
                    ActivityStack stack = (ActivityStack) mStacks.get(stackSize - 1);
                    if (DEBUG) {
                        Slog.d(TAG, "handleFocusScreen() : set focus on stack " + stack);
                    }
                    this.mActivityManager.setFocusedStack(stack.mStackId, true);
                }
            }
        }
    }

    private void handleReportScreenFocusChanged(DualScreen focusedScreen) {
        if (DEBUG) {
            Slog.d(TAG, "handleReportScreenFocusChanged() : focusedScreen=" + focusedScreen);
        }
        if (focusedScreen != null && focusedScreen != this.mFocusedScreen) {
            if (DEBUG) {
                Slog.d(TAG, "handleReportScreenFocusChanged() : broadcast new focus...");
            }
            this.mFocusedScreen = focusedScreen;
            int N = this.mDualScreenCallbacks.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    ((IDualScreenCallbacks) this.mDualScreenCallbacks.getBroadcastItem(i)).screenFocusChanged(focusedScreen);
                } catch (RemoteException | Exception e) {
                }
            }
            this.mDualScreenCallbacks.finishBroadcast();
        }
    }

    private void handleReportShrinkRequestedState(boolean shrinkRequested) {
        if (DEBUG) {
            Slog.d(TAG, "handleReportShrinkRequestedState() : shrinkRequested=" + shrinkRequested);
        }
        synchronized (this.mActivityManager) {
            ArrayList<ProcessRecord> lruProcesses = this.mActivityManager.mLruProcesses;
            for (int i = lruProcesses.size() - 1; i >= 0; i--) {
                ProcessRecord app = (ProcessRecord) lruProcesses.get(i);
                try {
                    if (app.thread != null) {
                        if (DEBUG) {
                            Slog.v(TAG, "update proc " + app.processName + " shrinkRequestedState to " + shrinkRequested);
                        }
                        app.thread.setShrinkRequestedState(shrinkRequested);
                    }
                } catch (Exception e) {
                }
            }
        }
    }

    private void handleReportSContextStatusChange(SContext event) {
       if (DEBUG) {
            Slog.d(TAG, "handleReportSContextStatusChange() : SContext=" + event);
        }
    }

    /* JADX WARNING: No exception handlers in catch block: Catch:{  } */
    /* JADX WARNING: Missing block: B:61:?, code skipped:
            return;
     */
    private void handleSwapTopTask() {
        /*
        r23 = this;
        r2 = DEBUG;
        if (r2 == 0) goto L_0x000c;
    L_0x0004:
        r2 = TAG;
        r4 = "handleSwapTopTask()";
        android.util.Slog.d(r2, r4);
    L_0x000c:
        r0 = r23;
        r0 = r0.mActivityManager;
        r22 = r0;
        monitor-enter(r22);
        r0 = r23;
        r2 = r0.mStackSupervisor;	 Catch:{ all -> 0x01ac }
        r20 = r2.topRunningActivityLocked();	 Catch:{ all -> 0x01ac }
        if (r20 == 0) goto L_0x0032;
    L_0x001d:
        r0 = r20;
        r2 = r0.dualScreenAttrs;	 Catch:{ all -> 0x01ac }
        r2 = r2.getDisplayId();	 Catch:{ all -> 0x01ac }
        r4 = 2;
        if (r2 != r4) goto L_0x0032;
    L_0x0028:
        r2 = TAG;	 Catch:{ all -> 0x01ac }
        r4 = "swapTopTask() : can't swap task in expanded screen";
        android.util.Slog.w(r2, r4);	 Catch:{ all -> 0x01ac }
        monitor-exit(r22);	 Catch:{ all -> 0x01ac }
    L_0x0031:
        return;
    L_0x0032:
        r2 = com.samsung.android.dualscreen.DualScreen.MAIN;	 Catch:{ all -> 0x01ac }
        r0 = r23;
        r3 = r0.getTopRunningTaskId(r2);	 Catch:{ all -> 0x01ac }
        r2 = com.samsung.android.dualscreen.DualScreen.SUB;	 Catch:{ all -> 0x01ac }
        r0 = r23;
        r17 = r0.getTopRunningTaskId(r2);	 Catch:{ all -> 0x01ac }
        r12 = 0;
        r18 = 0;
        r0 = r23;
        r2 = r0.mStackSupervisor;	 Catch:{ all -> 0x01ac }
        r11 = r2.anyTaskForIdLocked(r3);	 Catch:{ all -> 0x01ac }
        r0 = r23;
        r2 = r0.mStackSupervisor;	 Catch:{ all -> 0x01ac }
        r0 = r17;
        r16 = r2.anyTaskForIdLocked(r0);	 Catch:{ all -> 0x01ac }
        if (r11 == 0) goto L_0x005e;
    L_0x0059:
        r2 = 0;
        r12 = r11.topRunningActivityLocked(r2);	 Catch:{ all -> 0x01ac }
    L_0x005e:
        if (r16 == 0) goto L_0x0067;
    L_0x0060:
        r2 = 0;
        r0 = r16;
        r18 = r0.topRunningActivityLocked(r2);	 Catch:{ all -> 0x01ac }
    L_0x0067:
        r2 = DEBUG;	 Catch:{ all -> 0x01ac }
        if (r2 == 0) goto L_0x0090;
    L_0x006b:
        r2 = TAG;	 Catch:{ all -> 0x01ac }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x01ac }
        r4.<init>();	 Catch:{ all -> 0x01ac }
        r5 = "swapTopTask() : swap task and update informations. mainTopActivity=";
        r4 = r4.append(r5);	 Catch:{ all -> 0x01ac }
        r4 = r4.append(r12);	 Catch:{ all -> 0x01ac }
        r5 = " subTopActivity=";
        r4 = r4.append(r5);	 Catch:{ all -> 0x01ac }
        r0 = r18;
        r4 = r4.append(r0);	 Catch:{ all -> 0x01ac }
        r4 = r4.toString();	 Catch:{ all -> 0x01ac }
        android.util.Slog.d(r2, r4);	 Catch:{ all -> 0x01ac }
    L_0x0090:
        r4 = 1240002; // 0x12ebc2 float:1.737613E-39 double:6.126424E-318;
        r2 = 4;
        r5 = new java.lang.Object[r2];	 Catch:{ all -> 0x01ac }
        r2 = 0;
        r6 = java.lang.Integer.valueOf(r3);	 Catch:{ all -> 0x01ac }
        r5[r2] = r6;	 Catch:{ all -> 0x01ac }
        r6 = 1;
        if (r12 == 0) goto L_0x01af;
    L_0x00a0:
        r2 = r12.shortComponentName;	 Catch:{ all -> 0x01ac }
    L_0x00a2:
        r5[r6] = r2;	 Catch:{ all -> 0x01ac }
        r2 = 2;
        r6 = java.lang.Integer.valueOf(r17);	 Catch:{ all -> 0x01ac }
        r5[r2] = r6;	 Catch:{ all -> 0x01ac }
        r6 = 3;
        if (r18 == 0) goto L_0x01b4;
    L_0x00ae:
        r0 = r18;
        r2 = r0.shortComponentName;	 Catch:{ all -> 0x01ac }
    L_0x00b2:
        r5[r6] = r2;	 Catch:{ all -> 0x01ac }
        android.util.EventLog.writeEvent(r4, r5);	 Catch:{ all -> 0x01ac }
        r14 = android.os.Binder.clearCallingIdentity();	 Catch:{ all -> 0x01ac }
        r0 = r23;
        r2 = r0.mWindowManager;	 Catch:{ all -> 0x01c8 }
        r4 = 1;
        r21 = r2.getAnimationScale(r4);	 Catch:{ all -> 0x01c8 }
        r19 = 0;
        if (r12 == 0) goto L_0x0175;
    L_0x00c8:
        if (r18 == 0) goto L_0x0175;
    L_0x00ca:
        r0 = r23;
        r2 = r0.canSwapScreenLocked(r12);	 Catch:{ all -> 0x01c8 }
        if (r2 == 0) goto L_0x0175;
    L_0x00d2:
        r0 = r23;
        r1 = r18;
        r2 = r0.canSwapScreenLocked(r1);	 Catch:{ all -> 0x01c8 }
        if (r2 == 0) goto L_0x0175;
    L_0x00dc:
        r19 = 1;
        r0 = r23;
        r2 = r0.mWindowManager;	 Catch:{ all -> 0x01c8 }
        r4 = 1;
        r5 = 0;
        r2.setAnimationScale(r4, r5);	 Catch:{ all -> 0x01c8 }
        r0 = r23;
        r2 = r0.mActivityManager;	 Catch:{ all -> 0x01c8 }
        r2 = r2.mDualScreenPolicy;	 Catch:{ all -> 0x01c8 }
        r4 = com.samsung.android.dualscreen.DualScreen.SUB;	 Catch:{ all -> 0x01c8 }
        r2.updateScreenForAllActivitiesInTask(r11, r4);	 Catch:{ all -> 0x01c8 }
        r0 = r23;
        r2 = r0.mActivityManager;	 Catch:{ all -> 0x01c8 }
        r2 = r2.mDualScreenPolicy;	 Catch:{ all -> 0x01c8 }
        r4 = com.samsung.android.dualscreen.DualScreen.MAIN;	 Catch:{ all -> 0x01c8 }
        r0 = r16;
        r2.updateScreenForAllActivitiesInTask(r0, r4);	 Catch:{ all -> 0x01c8 }
        r0 = r23;
        r2 = r0.mStackSupervisor;	 Catch:{ all -> 0x01c8 }
        r4 = 0;
        r5 = 0;
        r10 = r2.computeStackFocus(r12, r4, r5);	 Catch:{ all -> 0x01c8 }
        r0 = r23;
        r2 = r0.mStackSupervisor;	 Catch:{ all -> 0x01c8 }
        r4 = 0;
        r5 = 0;
        r0 = r18;
        r13 = r2.computeStackFocus(r0, r4, r5);	 Catch:{ all -> 0x01c8 }
        r2 = 1;
        r12.frozenBeforeDestroy = r2;	 Catch:{ all -> 0x01c8 }
        r2 = 1;
        r0 = r18;
        r0.frozenBeforeDestroy = r2;	 Catch:{ all -> 0x01c8 }
        r2 = r12.app;	 Catch:{ all -> 0x01c8 }
        r2 = r12.mayFreezeScreenLocked(r2);	 Catch:{ all -> 0x01c8 }
        if (r2 == 0) goto L_0x012c;
    L_0x0125:
        r2 = r12.app;	 Catch:{ all -> 0x01c8 }
        r4 = 1048576; // 0x100000 float:1.469368E-39 double:5.180654E-318;
        r12.startFreezingScreenLocked(r2, r4);	 Catch:{ all -> 0x01c8 }
    L_0x012c:
        r0 = r18;
        r2 = r0.app;	 Catch:{ all -> 0x01c8 }
        r0 = r18;
        r2 = r0.mayFreezeScreenLocked(r2);	 Catch:{ all -> 0x01c8 }
        if (r2 == 0) goto L_0x0143;
    L_0x0138:
        r0 = r18;
        r2 = r0.app;	 Catch:{ all -> 0x01c8 }
        r4 = 1048576; // 0x100000 float:1.469368E-39 double:5.180654E-318;
        r0 = r18;
        r0.startFreezingScreenLocked(r2, r4);	 Catch:{ all -> 0x01c8 }
    L_0x0143:
        r0 = r23;
        r2 = r0.mStackSupervisor;	 Catch:{ all -> 0x01c8 }
        r4 = r10.mStackId;	 Catch:{ all -> 0x01c8 }
        r5 = 1;
        r6 = 0;
        r7 = 1;
        r2.moveTaskToStackLocked(r3, r4, r5, r6, r7);	 Catch:{ all -> 0x01c8 }
        r0 = r23;
        r4 = r0.mStackSupervisor;	 Catch:{ all -> 0x01c8 }
        r6 = r13.mStackId;	 Catch:{ all -> 0x01c8 }
        r7 = 1;
        r8 = 0;
        r9 = 1;
        r5 = r17;
        r4.moveTaskToStackLocked(r5, r6, r7, r8, r9);	 Catch:{ all -> 0x01c8 }
        r10.mResumedActivity = r12;	 Catch:{ all -> 0x01c8 }
        r0 = r18;
        r13.mResumedActivity = r0;	 Catch:{ all -> 0x01c8 }
        r0 = r23;
        r2 = r0.mActivityManager;	 Catch:{ all -> 0x01c8 }
        r2 = r2.mFrontActivities;	 Catch:{ all -> 0x01c8 }
        r4 = 0;
        r2[r4] = r18;	 Catch:{ all -> 0x01c8 }
        r0 = r23;
        r2 = r0.mActivityManager;	 Catch:{ all -> 0x01c8 }
        r2 = r2.mFrontActivities;	 Catch:{ all -> 0x01c8 }
        r4 = 1;
        r2[r4] = r12;	 Catch:{ all -> 0x01c8 }
    L_0x0175:
        r0 = r23;
        r2 = r0.mWindowManager;	 Catch:{ all -> 0x01c8 }
        r4 = 1;
        r0 = r21;
        r2.setAnimationScale(r4, r0);	 Catch:{ all -> 0x01c8 }
        if (r19 != 0) goto L_0x01b9;
    L_0x0181:
        r2 = TAG;	 Catch:{ all -> 0x01c8 }
        r4 = new java.lang.StringBuilder;	 Catch:{ all -> 0x01c8 }
        r4.<init>();	 Catch:{ all -> 0x01c8 }
        r5 = "swapTopTask() : fail to swap - main : ";
        r4 = r4.append(r5);	 Catch:{ all -> 0x01c8 }
        r4 = r4.append(r12);	 Catch:{ all -> 0x01c8 }
        r5 = " sub : ";
        r4 = r4.append(r5);	 Catch:{ all -> 0x01c8 }
        r0 = r18;
        r4 = r4.append(r0);	 Catch:{ all -> 0x01c8 }
        r4 = r4.toString();	 Catch:{ all -> 0x01c8 }
        android.util.Slog.d(r2, r4);	 Catch:{ all -> 0x01c8 }
        android.os.Binder.restoreCallingIdentity(r14);	 Catch:{ all -> 0x01ac }
        monitor-exit(r22);	 Catch:{ all -> 0x01ac }
        goto L_0x0031;
    L_0x01ac:
        r2 = move-exception;
        monitor-exit(r22);	 Catch:{ all -> 0x01ac }
        throw r2;
    L_0x01af:
        r2 = "null";
        goto L_0x00a2;
    L_0x01b4:
        r2 = "null";
        goto L_0x00b2;
    L_0x01b9:
        r0 = r23;
        r2 = r0.mStackSupervisor;	 Catch:{ all -> 0x01c8 }
        r4 = 0;
        r5 = 0;
        r2.ensureActivitiesVisibleLocked(r4, r5);	 Catch:{ all -> 0x01c8 }
        android.os.Binder.restoreCallingIdentity(r14);	 Catch:{ all -> 0x01ac }
        monitor-exit(r22);	 Catch:{ all -> 0x01ac }
        goto L_0x0031;
    L_0x01c8:
        r2 = move-exception;
        android.os.Binder.restoreCallingIdentity(r14);	 Catch:{ all -> 0x01ac }
        throw r2;	 Catch:{ all -> 0x01ac }
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.server.am.DualScreenManagerService.handleSwapTopTask():void");
    }

    private void moveTaskToScreen(int taskId, DualScreen toScreen, int flags, Bundle options) {
    }

    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (this.mActivityManager.checkCallingPermission("android.permission.DUMP") != 0) {
            pw.println("Permission Denial: can't dump DualScreenManager from from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid() + " without permission " + "android.permission.DUMP");
            return;
        }
        boolean dumpAll = false;
        int opti = 0;
        while (opti < args.length) {
            String opt = args[opti];
            if (opt == null || opt.length() <= 0 || opt.charAt(0) != '-') {
                break;
            }
            opti++;
            if ("-h".equals(opt)) {
                pw.println("DualScreen manager dump options:");
                pw.println("  [-h] [cmd] ...");
                pw.println("  cmd may be one of:");
                pw.println("    c[ontext]: context relation informations");
                pw.println("    cc: context relation informations with call stack information");
                pw.println("    cs: simple context relation informations");
                pw.println("    s[ettings]: dual screen settings");
                pw.println("  -a: include all available server state");
                return;
            } else if ("-a".equals(opt)) {
                dumpAll = true;
            } else {
                pw.println("Unknown argument: " + opt + "; use -h for help");
            }
        }
        long origId = Binder.clearCallingIdentity();
        if (dumpAll) {
            try {
                synchronized (this.mActivityManager) {
                    dumpServerLocked("  ", pw);
                    dumpSettingsLocked("  ", pw);
                    dumpContextRelationsLocked("  ", fd, pw, args, null);
                }
                Binder.restoreCallingIdentity(origId);
            } catch (Throwable th) {
                Binder.restoreCallingIdentity(origId);
            }
        } else {
            if (opti < args.length) {
                String cmd = args[opti];
                opti++;
                if ("context".equals(cmd) || "c".equals(cmd) || "cc".equals(cmd) || "cs".equals(cmd)) {
                    String processName = null;
                    if (opti < args.length) {
                        processName = args[opti];
                    }
                    synchronized (this.mActivityManager) {
                        dumpContextRelationsLocked("  ", fd, pw, args, processName);
                    }
                } else if ("settings".equals(cmd) || "s".equals(cmd)) {
                    synchronized (this.mActivityManager) {
                        dumpSettingsLocked("  ", pw);
                    }
                }
            } else {
                synchronized (this.mActivityManager) {
                    dumpServerLocked("  ", pw);
                    dumpSettingsLocked("  ", pw);
                }
            }
            Binder.restoreCallingIdentity(origId);
        }
    }

    private void dumpServerLocked(String prefix, PrintWriter pw) {
        String innerPrefix = prefix + "  ";
        pw.print(prefix);
        pw.println("DUALSCREEN MANAGER (dumpsys dualscreen)");
        pw.print(innerPrefix);
        pw.print("mFocusedScreen=");
        pw.println(this.mFocusedScreen);
        pw.println();
        pw.print(innerPrefix);
        pw.print("mSingleScreenState=");
        pw.println(this.mActivityManager.mDualScreenPolicy.mSingleScreenState);
        pw.print(innerPrefix);
        pw.print("mExpandedHomeStatus=");
        pw.println(this.mActivityManager.mDualScreenPolicy.mExpandedHomeStatus);
        pw.println();
    }

    private void dumpSettingsLocked(String prefix, PrintWriter pw) {
        String innerPrefix = prefix + "  ";
        pw.print(prefix);
        pw.println("DUALSCREEN MANAGER settings (dumpsys dualscreen settings)");
        DualScreenSettings.dump(innerPrefix, pw);
        pw.println();
    }

    private void dumpContextRelationsLocked(String prefix, FileDescriptor fd, PrintWriter pw, String[] args, String processName) {
        String innerPrefix = prefix + "  ";
        pw.print(prefix);
        pw.println("DUALSCREEN MANAGER CONTEXTS RELATION (dumpsys dualscreen context)");
        int NP = this.mActivityManager.mProcessNames.getMap().size();
        for (int ip = 0; ip < NP; ip++) {
            SparseArray<ProcessRecord> procs = (SparseArray) this.mActivityManager.mProcessNames.getMap().valueAt(ip);
            int NA = procs.size();
            for (int ia = 0; ia < NA; ia++) {
                ProcessRecord app = (ProcessRecord) procs.valueAt(ia);
                if (!(app == null || app.thread == null)) {
                    if (processName != null) {
                        if (!processName.equals(app.processName)) {
                            continue;
                        }
                    }
                    pw.print(innerPrefix);
                    pw.print("PID ");
                    pw.print(app.pid);
                    pw.print(" ProcessRecord{");
                    pw.print(app.processName);
                    pw.print(" PSS:");
                    pw.print(app.lastPss);
                    pw.println(" }");
                    pw.flush();
                    TransferPipe tp;
                    try {
                        tp = new TransferPipe();
                        if (DEBUG) {
                            Slog.d(TAG, "dumpContextRelationsLocked() : app=" + app);
                        }
                        if (app.pid == ActivityManagerService.MY_PID) {
                            new AnonymousClass1TransferPipeThread(app.thread, tp.getWriteFd().getFileDescriptor(), args, innerPrefix).start();
                            try {
                                Thread.sleep(200);
                            } catch (InterruptedException e) {
                            }
                        } else {
                            app.thread.dumpContextRelationInfo(tp.getWriteFd().getFileDescriptor(), innerPrefix, args);
                        }
                        tp.go(fd);
                        tp.kill();
                    } catch (IOException e2) {
                        pw.println(innerPrefix + "Failure while dumping the context relation of the activity: " + e2);
                    } catch (RemoteException e3) {
                        pw.println(innerPrefix + "Got a RemoteException while dumping the context relation of the activity");
                    } catch (Throwable th) {
                        tp.kill();
                    }
                }
            }
        }
    }
}

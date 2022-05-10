package com.tanodxyz.gdownload.executors;

import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.core.util.Pair;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This main contract of this class is to run scheduled callbacks or piece of code
 * after regular interval, and these callbacks are cancelable, have start and end bounds.
 * End Bounds are Not Honoured by LifeCycle
 * //!! see {@linkplain TimeBounds}
 */
public class CallbacksScheduler implements DefaultLifecycleObserver {

    public static String TAG = "callbackScheduler";
    private ScheduledExecutorService ses = null;
    private static final AtomicInteger UNIQUE_ID = new AtomicInteger();
    private Lifecycle lifeCycle;
    private final SparseArray<Pair<ScheduledFuture<?>, TimeBasedCallback>> callbacks = new SparseArray<>();
    private final Object lock = new Object();
    private boolean startStopWithActivityLifeCycle = true;

    public CallbacksScheduler(@Nullable Lifecycle lifecycle, boolean startStopWithActivityLifeCycle, ScheduledThreadPoolExecutor ses) {
        this.startStopWithActivityLifeCycle = startStopWithActivityLifeCycle;
        setLifecycle(lifecycle);
        this.ses = ses;
    }

    private static int getUniqueId() {
        return UNIQUE_ID.incrementAndGet();
    }

    public void setLifecycle(Lifecycle lifecycle) {
        if (lifecycle == null) {
            stopObservingLifeCycle();
        } else {
            stopObservingLifeCycle();
            lifecycle.addObserver(CallbacksScheduler.this);
        }
        this.lifeCycle = lifecycle;
    }

    private void stopObservingLifeCycle() {
        if (this.lifeCycle != null) {
            this.lifeCycle.removeObserver(this);
        }
    }

    public CallbacksScheduler(@NonNull ScheduledThreadPoolExecutor ses) {
        this(null, false, ses);
    }

    public CallbacksScheduler(@Nullable Lifecycle lifeCycle) {
        setLifecycle(lifeCycle);
    }

    public void setExecutor(ScheduledThreadPoolExecutor ses) {
        this.ses = ses;
    }

    public TimeBasedCallback schedule(Consumer<TimeBasedCallback> action, long after, TimeUnit timeUnit) {
        final TimeBounds timeBounds = TimeBounds.INFINITE;
        timeBounds.delay = after;
        timeBounds.end = 0;
        timeBounds.pointer = 0;
        timeBounds.delayUnit = timeUnit;
        final TimeBasedCallback timeBasedCallback = new TimeBasedCallback(getUniqueId(), timeBounds, CallbackState.STARTED, action);
        ScheduledFuture<?> scheduledFuture = ses.scheduleAtFixedRate(() -> {
            TimeBounds tb = timeBasedCallback.getTimeBound();
            tb.pointer += tb.delay;
            timeBasedCallback.action.accept(timeBasedCallback);
        }, 0, timeBasedCallback.timeBound.delay, timeBasedCallback.timeBound.delayUnit);
        synchronized (lock) {
            callbacks.put(timeBasedCallback.uniqueId, new Pair<>(scheduledFuture, timeBasedCallback));
        }
        return timeBasedCallback;
    }

    public TimeBasedCallback schedule(Consumer<TimeBasedCallback> callback, long after, TimeUnit afterTimeUnit, long end, TimeUnit endTimeUnit) {
        final TimeBounds timeBounds = TimeBounds.FIX;
        timeBounds.delay = after;
        timeBounds.pointer = 0;
        timeBounds.delayUnit = afterTimeUnit;
        timeBounds.end = end;
        timeBounds.endUnit = endTimeUnit;
        final TimeBasedCallback timeBasedCallback = new TimeBasedCallback(getUniqueId(), timeBounds, CallbackState.STARTED, callback);
        ScheduledFuture<?> scheduledFuture = ses.scheduleAtFixedRate(() -> {
            TimeBounds tb = timeBasedCallback.getTimeBound();
            tb.pointer += tb.delay;
            long convert = timeBounds.endUnit.convert(tb.pointer, tb.delayUnit);
            if (convert >= tb.end) {
                stop(timeBasedCallback.uniqueId);
            }
            timeBasedCallback.action.accept(timeBasedCallback);

        }, 0, timeBasedCallback.timeBound.delay, timeBasedCallback.timeBound.delayUnit);
        synchronized (lock) {
            callbacks.put(timeBasedCallback.uniqueId, new Pair<>(scheduledFuture, timeBasedCallback));
        }
        return timeBasedCallback;
    }

    public TimeBasedCallback schedule(TimeBasedCallback timeBasedCallback) {
        final TimeBounds timeBounds = timeBasedCallback.timeBound;
        ScheduledFuture<?> scheduledFuture;
        if (timeBounds == TimeBounds.FIX) {
            scheduledFuture = ses.scheduleAtFixedRate(() -> {
                TimeBounds tb = timeBasedCallback.getTimeBound();
                tb.pointer += tb.delay;
                long convert = timeBounds.endUnit.convert(tb.pointer, tb.delayUnit);
                if (convert >= tb.end) {
                    stop(timeBasedCallback.uniqueId);
                }
                timeBasedCallback.action.accept(timeBasedCallback);

            }, 0, timeBasedCallback.timeBound.delay, timeBasedCallback.timeBound.delayUnit);
        } else {
            scheduledFuture = ses.scheduleAtFixedRate(() -> {
                TimeBounds tb = timeBasedCallback.getTimeBound();
                tb.pointer += tb.delay;
                timeBasedCallback.action.accept(timeBasedCallback);
            }, 0, timeBasedCallback.timeBound.delay, timeBasedCallback.timeBound.delayUnit);
        }
        synchronized (lock) {
            callbacks.remove(timeBasedCallback.uniqueId);
            callbacks.put(timeBasedCallback.uniqueId, new Pair<>(scheduledFuture, timeBasedCallback));
        }
        return timeBasedCallback;
    }

    public void setStartStopWithActivityLifeCycle(boolean startStopWithActivityLifeCycle) {
        this.startStopWithActivityLifeCycle = startStopWithActivityLifeCycle;
    }

    public void restart(int id) {
        synchronized (lock) {
            Pair<ScheduledFuture<?>, TimeBasedCallback> scheduledFutureTimeBasedCallbackPair = callbacks.get(id);
            if (scheduledFutureTimeBasedCallbackPair != null) {
                final TimeBasedCallback data = scheduledFutureTimeBasedCallbackPair.second;
                if (data.getCurrentState() == CallbackState.STARTED) {
                    scheduledFutureTimeBasedCallbackPair.first.cancel(true);
                    data.getTimeBound().pointer = 0;
                }
                data.setCurrentState(CallbackState.STARTED);
                schedule(data);
            }
        }
    }

    public void pause(int id) {
        synchronized (lock) {
            Pair<ScheduledFuture<?>, TimeBasedCallback> scheduledFutureTimeBasedCallbackPair = callbacks.get(id);
            if (scheduledFutureTimeBasedCallbackPair != null && scheduledFutureTimeBasedCallbackPair.second.getCurrentState() == CallbackState.STARTED) {
                scheduledFutureTimeBasedCallbackPair.second.setCurrentState(CallbackState.PAUSED);
                scheduledFutureTimeBasedCallbackPair.first.cancel(true);
            }
        }
    }

    public void resume(int id) {
        synchronized (lock) {
            Pair<ScheduledFuture<?>, TimeBasedCallback> scheduledFutureTimeBasedCallbackPair = callbacks.get(id);
            if (scheduledFutureTimeBasedCallbackPair != null && scheduledFutureTimeBasedCallbackPair.second.getCurrentState() == CallbackState.PAUSED) {
                final TimeBasedCallback data = scheduledFutureTimeBasedCallbackPair.second;
                data.setCurrentState(CallbackState.STARTED);
                schedule(data);
            }
        }
    }

    public void stopAndRemove(int id) {
        stop(id);
        synchronized (lock) {
            callbacks.remove(id);
        }
    }

    public void stop(int id) {
        synchronized (lock) {
            Pair<ScheduledFuture<?>, TimeBasedCallback> scheduledFutureTimeBasedCallbackPair = callbacks.get(id);
            if (scheduledFutureTimeBasedCallbackPair != null) {
                scheduledFutureTimeBasedCallbackPair.second.setCurrentState(CallbackState.STOPPED);
                scheduledFutureTimeBasedCallbackPair.first.cancel(true);
                callbacks.remove(id);
            }
        }
    }

    public void pause(Pair<ScheduledFuture<?>, TimeBasedCallback> scheduledFutureTimeBasedCallbackPair) {
        synchronized (lock) {
            if (scheduledFutureTimeBasedCallbackPair != null && scheduledFutureTimeBasedCallbackPair.second.getCurrentState() == CallbackState.STARTED) {
                scheduledFutureTimeBasedCallbackPair.second.setCurrentState(CallbackState.PAUSED);
                scheduledFutureTimeBasedCallbackPair.first.cancel(true);
            }
        }
    }

    public void resume(Pair<ScheduledFuture<?>, TimeBasedCallback> scheduledFutureTimeBasedCallbackPair) {
        synchronized (lock) {
            if (scheduledFutureTimeBasedCallbackPair != null && scheduledFutureTimeBasedCallbackPair.second.getCurrentState() == CallbackState.PAUSED) {
                final TimeBasedCallback data = scheduledFutureTimeBasedCallbackPair.second;
                data.setCurrentState(CallbackState.STARTED);
                schedule(data);
            }
        }
    }

    public void setAfterTime(int id, long after, TimeUnit timeUnit) {
        synchronized (lock) {
            Pair<ScheduledFuture<?>, TimeBasedCallback> scheduledFutureTimeBasedCallbackPair = callbacks.get(id);
            if (scheduledFutureTimeBasedCallbackPair != null) {
                final TimeBasedCallback data = scheduledFutureTimeBasedCallbackPair.second;
                TimeBounds timeBound = data.getTimeBound();
                timeBound.delay = after;
                timeBound.delayUnit = timeUnit;
            }
        }
    }

    public void setEndTime(int id, long end, TimeUnit timeUnit) {
        synchronized (lock) {
            Pair<ScheduledFuture<?>, TimeBasedCallback> scheduledFutureTimeBasedCallbackPair = callbacks.get(id);
            if (scheduledFutureTimeBasedCallbackPair != null) {
                final TimeBasedCallback data = scheduledFutureTimeBasedCallbackPair.second;
                TimeBounds timeBound = data.getTimeBound();
                timeBound.end = end;
                timeBound.endUnit = timeUnit;
            }
        }
    }

    public void skipForward(int id, int timesDelay) {
        synchronized (lock) {
            Pair<ScheduledFuture<?>, TimeBasedCallback> scheduledFutureTimeBasedCallbackPair = callbacks.get(id);
            if (scheduledFutureTimeBasedCallbackPair != null) {
                final TimeBasedCallback data = scheduledFutureTimeBasedCallbackPair.second;
                TimeBounds timeBound = data.getTimeBound();
                timeBound.pointer += (timesDelay * timeBound.delay);
            }
        }
    }

    public void skipBackward(int id, long timesDelay) {
        synchronized (lock) {
            Pair<ScheduledFuture<?>, TimeBasedCallback> scheduledFutureTimeBasedCallbackPair = callbacks.get(id);
            if (scheduledFutureTimeBasedCallbackPair != null) {
                final TimeBasedCallback data = scheduledFutureTimeBasedCallbackPair.second;
                TimeBounds timeBound = data.getTimeBound();
                timeBound.pointer -= (timesDelay * timeBound.delay);
            }
        }
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        DefaultLifecycleObserver.super.onDestroy(owner);
        if (ses != null) {
            ses.shutdownNow();
        }
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        DefaultLifecycleObserver.super.onStop(owner);
        if (null != ses && startStopWithActivityLifeCycle) {
            ses.submit(() -> {
                for (int i = 0; i < callbacks.size(); ++i) {
                    Pair<ScheduledFuture<?>, TimeBasedCallback> scheduledFutureTimeBasedCallbackPair;
                    synchronized (lock) {
                        scheduledFutureTimeBasedCallbackPair = callbacks.valueAt(i);
                    }
                    pause(scheduledFutureTimeBasedCallbackPair);
                }
            });
        }
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        DefaultLifecycleObserver.super.onResume(owner);
        if (null != ses && startStopWithActivityLifeCycle) {
            ses.submit(() -> {
                for (int i = 0; i < callbacks.size(); ++i) {
                    Pair<ScheduledFuture<?>, TimeBasedCallback> scheduledFutureTimeBasedCallbackPair;
                    synchronized (lock) {
                        scheduledFutureTimeBasedCallbackPair = callbacks.valueAt(i);
                    }
                    resume(scheduledFutureTimeBasedCallbackPair);
                }
            });
        }
    }

    public static class TimeBasedCallback {
        public TimeBasedCallback(int id, TimeBounds timeBound, CallbackState callbackState, Consumer<TimeBasedCallback> action) {
            this.uniqueId = id;
            this.timeBound = timeBound;
            this.currentState = callbackState;
            this.action = action;
        }

        private Consumer<TimeBasedCallback> action;
        private int uniqueId;
        private TimeBounds timeBound;
        private CallbackState currentState;

        public synchronized CallbackState getCurrentState() {
            return currentState;
        }

        public synchronized int getUniqueId() {
            return uniqueId;
        }

        public synchronized Consumer<TimeBasedCallback> getAction() {
            return action;
        }

        public synchronized TimeBounds getTimeBound() {
            return timeBound;
        }

        synchronized void setAction(Consumer<TimeBasedCallback> action) {
            this.action = action;
        }

        synchronized void setCurrentState(CallbackState currentState) {
            this.currentState = currentState;
        }

        synchronized void setTimeBound(TimeBounds timeBound) {
            this.timeBound = timeBound;
        }

        synchronized void setUniqueId(int uniqueId) {
            this.uniqueId = uniqueId;
        }
    }

    public enum TimeBounds {
        INFINITE, FIX;
        public long end, delay, pointer;
        public TimeUnit delayUnit, endUnit;

    }

    public enum CallbackState {
        IDLE, STARTED, PAUSED, STOPPED
    }
}

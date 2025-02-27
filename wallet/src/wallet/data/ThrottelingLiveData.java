/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package wallet.data;

import android.os.Handler;
import androidx.annotation.MainThread;
import androidx.lifecycle.LiveData;

import java.time.Duration;
import java.time.Instant;

public abstract class ThrottelingLiveData<T> extends LiveData<T> {
    private final Duration throttle;
    private final Handler handler = new Handler();
    private Instant lastMessageTime = null;
    private static final Duration DEFAULT_THROTTLE = Duration.ofMillis(500);

    public ThrottelingLiveData() {
        this(DEFAULT_THROTTLE);
    }

    public ThrottelingLiveData(final Duration throttle) {
        this.throttle = throttle;
    }

    @Override
    protected void onInactive() {
        super.onInactive();
        handler.removeCallbacksAndMessages(null);
    }

    @MainThread
    protected void triggerLoad() {
        handler.removeCallbacksAndMessages(null);
        final Runnable runnable = () -> {
            lastMessageTime = Instant.now();
            load();
        };
        if (lastMessageTime == null) {
            runnable.run(); // load immediately, because it's the first time
        } else {
            final Duration lastMessageAgo = Duration.between(lastMessageTime, Instant.now());
            if (lastMessageAgo.compareTo(throttle) < 0)
                handler.postDelayed(runnable, throttle.minus(lastMessageAgo).toMillis()); // throttled load
            else
                runnable.run(); // load immediately, because it's been a while
        }
    }

    @MainThread
    protected void load() {
        // do nothing by default
    }
}

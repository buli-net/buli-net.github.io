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

package wallet.service;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wallet.Configuration;
import wallet.Constants;
import wallet.WalletApplication;

import java.time.Duration;

public class StartBlockchainService extends JobService {
    private PowerManager pm;

    private static final Logger log = LoggerFactory.getLogger(StartBlockchainService.class);

    public static void schedule(final WalletApplication application, final boolean expectLargeData) {
        final Configuration config = application.getConfiguration();
        final Duration lastUsedAgo = config.getLastUsedAgo();

        // apply some backoff
        final Duration interval;
        if (lastUsedAgo.compareTo(Constants.LAST_USAGE_THRESHOLD_JUST) < 0)
            interval = Duration.ofMinutes(15);
        else if (lastUsedAgo.compareTo(Constants.LAST_USAGE_THRESHOLD_TODAY) < 0)
            interval = Duration.ofHours(1);
        else if (lastUsedAgo.compareTo(Constants.LAST_USAGE_THRESHOLD_RECENTLY) < 0)
            interval = Duration.ofHours(12);
        else
            interval = Duration.ofDays(1);

        log.info("last used {} minutes ago{}, rescheduling block chain sync in roughly {} minutes",
                lastUsedAgo.toMinutes(), expectLargeData ? " and expecting large data" : "",
                interval.toMinutes());

        final JobScheduler jobScheduler = application.getSystemService(JobScheduler.class);
        final JobInfo.Builder jobInfo = new JobInfo.Builder(0, new ComponentName(application,
                StartBlockchainService.class));
        jobInfo.setMinimumLatency(interval.toMillis());
        jobInfo.setOverrideDeadline(Duration.ofDays(7).toMillis());
        jobInfo.setRequiredNetworkType(expectLargeData ? JobInfo.NETWORK_TYPE_UNMETERED : JobInfo.NETWORK_TYPE_ANY);
        jobInfo.setRequiresDeviceIdle(true);
        jobInfo.setRequiresBatteryNotLow(true);
        jobInfo.setRequiresStorageNotLow(true);
        jobScheduler.schedule(jobInfo.build());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        pm = getSystemService(PowerManager.class);
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        final boolean storageLow = registerReceiver(null, new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW)) != null;
        final boolean batteryLow = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_LOW)) != null;
        final boolean powerSaveMode = pm.isPowerSaveMode();
        if (storageLow)
            log.info("storage low, not starting block chain sync");
        if (batteryLow)
            log.info("battery low, not starting block chain sync");
        if (powerSaveMode)
            log.info("power save mode, not starting block chain sync");
        if (!storageLow && !batteryLow && !powerSaveMode)
            BlockchainService.start(this, false);
        return false;
    }

    @Override
    public boolean onStopJob(final JobParameters params) {
        return false;
    }
}

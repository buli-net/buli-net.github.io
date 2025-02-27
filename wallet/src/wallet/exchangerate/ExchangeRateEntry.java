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

package wallet.exchangerate;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverter;
import androidx.room.TypeConverters;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.utils.Fiat;
import org.bitcoinj.utils.ExchangeRate;

import java.time.Instant;

@Entity(tableName = ExchangeRateEntry.TABLE_NAME, indices = { @Index(value = { "source", "currency_code" },
        unique = true) })
public final class ExchangeRateEntry {
    public static final String TABLE_NAME = "exchange_rates";

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    @NonNull
    @ColumnInfo(name = "source")
    private String source;

    @NonNull
    @ColumnInfo(name = "currency_code")
    private String currencyCode;

    @NonNull
    @ColumnInfo(name = "rate_timestamp")
    @TypeConverters({ TimeConverters.class })
    private Instant rateTimeStamp;

    @ColumnInfo(name = "rate_coin")
    private long rateCoin;

    @ColumnInfo(name = "rate_fiat")
    private long rateFiat;

    public ExchangeRateEntry(final long id, @NonNull final String source, @NonNull final String currencyCode,
                             @NonNull final Instant rateTimeStamp, final long rateCoin, final long rateFiat) {
        this.id = id;
        this.source = source;
        this.currencyCode = currencyCode;
        this.rateTimeStamp = rateTimeStamp;
        this.rateCoin = rateCoin;
        this.rateFiat = rateFiat;
    }

    public ExchangeRateEntry(final String source, final ExchangeRate exchangeRate) {
        this.source = source;
        this.currencyCode = exchangeRate.fiat.currencyCode;
        this.rateTimeStamp = Instant.now();
        this.rateCoin = exchangeRate.coin.value;
        this.rateFiat = exchangeRate.fiat.value;
    }

    public long getId() {
        return id;
    }

    @NonNull
    public String getSource() {
        return source;
    }

    @NonNull
    public String getCurrencyCode() {
        return currencyCode;
    }

    @NonNull
    public Instant getRateTimeStamp() {
        return rateTimeStamp;
    }

    public long getRateCoin() {
        return rateCoin;
    }

    public long getRateFiat() {
        return rateFiat;
    }

    @NonNull
    public Coin coin() {
        return Coin.valueOf(rateCoin);
    }

    @NonNull
    public Fiat fiat() {
        return Fiat.valueOf(currencyCode, rateFiat);
    }

    @NonNull
    public ExchangeRate exchangeRate() {
        return new ExchangeRate(coin(), fiat());
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(getClass().getSimpleName());
        builder.append('[');
        builder.append(fiat().toFriendlyString());
        builder.append(" per ");
        builder.append(coin().toFriendlyString());
        builder.append(']');
        return builder.toString();
    }

    public static final class TimeConverters {
        @TypeConverter
        public static Instant millisToInstant(final long millis) {
            return Instant.ofEpochMilli(millis);
        }

        @TypeConverter
        public static long instantToMillis(final Instant time) {
            return time.toEpochMilli();
        }
    }
}

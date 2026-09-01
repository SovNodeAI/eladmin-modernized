/*
 *  Modified by SovNode in 2026 (Spring Boot 4.1 modernization); see NOTICE.
 */
package me.zhengjie.utils;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("behavioral")
public class DateUtilsTest {

    @Test
    public void fromTimeStampMatchesDefaultOffsetConversion() {
        long timestamp = 1_700_000_000L;
        LocalDateTime converted = DateUtil.fromTimeStamp(timestamp);
        LocalDateTime expected = LocalDateTime.ofEpochSecond(timestamp, 0,
                OffsetDateTime.now().getOffset());
        assertEquals(expected, converted,
                "fromTimeStamp must convert epoch seconds with the current offset");
    }

    @Test
    public void localDateTimeFormatyMdHmsProducesTheCanonicalPattern() {
        LocalDateTime fixed = LocalDateTime.of(2023, 11, 14, 22, 13, 20);
        assertEquals("2023-11-14 22:13:20", DateUtil.localDateTimeFormatyMdHms(fixed));
    }

    @Test
    public void dateConversionsRoundTrip() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Date date = DateUtil.toDate(now);
        LocalDateTime back = DateUtil.toLocalDateTime(date);
        assertEquals(now, back,
                "toDate followed by toLocalDateTime must keep the value");
        LocalDateTime fromEpoch = DateUtil.fromTimeStamp(date.getTime() / 1000);
        assertEquals(now, fromEpoch,
                "fromTimeStamp on the same instant must reproduce the original value");
        assertEquals(Instant.ofEpochSecond(now.atZone(ZoneId.systemDefault()).toEpochSecond())
                .atZone(ZoneId.systemDefault()).toLocalDateTime(), fromEpoch);
    }
}

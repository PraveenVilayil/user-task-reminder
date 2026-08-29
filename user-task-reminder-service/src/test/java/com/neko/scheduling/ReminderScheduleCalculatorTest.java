package com.neko.scheduling;

import com.neko.entity.Reminder;
import com.neko.exceptions.ErrorCode.ErrorCode;
import com.neko.exceptions.UserTaskReminderException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The fire-time arithmetic is the heart of the scheduler, so it is tested
 * against fixed instants rather than by waiting for anything to happen.
 */
class ReminderScheduleCalculatorTest {

    private static final LocalDateTime MONDAY_08_00 = LocalDateTime.of(2026, 3, 2, 8, 0);

    private final ReminderScheduleCalculator calculator = new ReminderScheduleCalculator();

    @Nested
    @DisplayName("cron validation")
    class CronValidation {

        @ParameterizedTest
        @ValueSource(strings = {"0 0 9 * * *", "0 0 9 * * MON-FRI", "0 */15 * * * *", "0 0 0 1 1 *"})
        void acceptsValidExpressions(String cron) {
            calculator.validate(cron);
        }

        @ParameterizedTest
        @ValueSource(strings = {"not-a-cron", "0 0 9 * *", "99 0 9 * * *", "@yearly-ish"})
        void rejectsInvalidExpressions(String cron) {
            assertThatThrownBy(() -> calculator.validate(cron))
                    .isInstanceOf(UserTaskReminderException.class)
                    .extracting(exception -> ((UserTaskReminderException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_CRON_EXPRESSION);
        }

        @Test
        void rejectsNullAndBlank() {
            assertThatThrownBy(() -> calculator.validate(null))
                    .isInstanceOf(UserTaskReminderException.class);
            assertThatThrownBy(() -> calculator.validate("   "))
                    .isInstanceOf(UserTaskReminderException.class);
        }
    }

    @Nested
    @DisplayName("next fire time")
    class NextFireTime {

        @Test
        void isStrictlyAfterTheReferenceInstant() {
            LocalDateTime nineAm = LocalDateTime.of(2026, 3, 2, 9, 0);

            Optional<LocalDateTime> next = calculator.nextFireTime("0 0 9 * * *", nineAm);

            assertThat(next).contains(nineAm.plusDays(1));
        }

        @Test
        void findsTheSameDayOccurrenceWhenItHasNotPassed() {
            Optional<LocalDateTime> next = calculator.nextFireTime("0 0 9 * * *", MONDAY_08_00);

            assertThat(next).contains(LocalDateTime.of(2026, 3, 2, 9, 0));
        }

        @Test
        void honoursDayOfWeekRestrictions() {
            LocalDateTime fridayEvening = LocalDateTime.of(2026, 3, 6, 18, 0);

            Optional<LocalDateTime> next = calculator.nextFireTime("0 0 9 * * MON-FRI", fridayEvening);

            // Saturday and Sunday are skipped.
            assertThat(next).contains(LocalDateTime.of(2026, 3, 9, 9, 0));
        }
    }

    @Nested
    @DisplayName("initial fire time")
    class InitialFireTime {

        @Test
        void forAOneTimeReminderIsItsDueDate() {
            Reminder reminder = new Reminder();
            reminder.setDueDate(LocalDateTime.of(2026, 3, 2, 17, 30));

            assertThat(calculator.initialFireTime(reminder, MONDAY_08_00))
                    .contains(LocalDateTime.of(2026, 3, 2, 17, 30));
        }

        @Test
        void forARecurringReminderIsTheFirstCronOccurrence() {
            Reminder reminder = new Reminder();
            reminder.setCron("0 30 8 * * *");

            assertThat(calculator.initialFireTime(reminder, MONDAY_08_00))
                    .contains(LocalDateTime.of(2026, 3, 2, 8, 30));
        }

        @Test
        void isEmptyForAOneTimeReminderWithNoDueDate() {
            assertThat(calculator.initialFireTime(new Reminder(), MONDAY_08_00)).isEmpty();
        }
    }

    @Nested
    @DisplayName("catch-up after downtime")
    class CatchUp {

        @Test
        void skipsEveryMissedOccurrenceAndLandsAfterNow() {
            LocalDateTime lastFire = LocalDateTime.of(2026, 3, 2, 9, 0);
            LocalDateTime now = LocalDateTime.of(2026, 3, 6, 10, 0);

            Optional<LocalDateTime> next = calculator.catchUp("0 0 9 * * *", lastFire, now);

            // Four days of 09:00 firings were missed; only the next future one is scheduled.
            assertThat(next).contains(LocalDateTime.of(2026, 3, 7, 9, 0));
        }

        @Test
        void behavesLikeANormalAdvanceWhenNothingWasMissed() {
            LocalDateTime now = LocalDateTime.of(2026, 3, 2, 9, 0);

            assertThat(calculator.catchUp("0 0 9 * * *", now, now))
                    .contains(LocalDateTime.of(2026, 3, 3, 9, 0));
        }

        @Test
        void toleratesANullPreviousFireTime() {
            assertThat(calculator.catchUp("0 0 9 * * *", null, MONDAY_08_00))
                    .contains(LocalDateTime.of(2026, 3, 2, 9, 0));
        }

        @Test
        void rollsForwardAcrossYearBoundariesWithoutReplayingEveryOccurrence() {
            // Six years of new-year firings were missed; only the next one is scheduled.
            Optional<LocalDateTime> next = calculator.catchUp(
                    "0 0 0 1 1 *", LocalDateTime.of(2020, 1, 1, 0, 0), LocalDateTime.of(2026, 3, 6, 10, 0));

            assertThat(next).contains(LocalDateTime.of(2027, 1, 1, 0, 0));
        }
    }

    @Nested
    @DisplayName("due check")
    class DueCheck {

        @Test
        void isDueWhenTheFireTimeHasArrived() {
            Reminder reminder = new Reminder();
            reminder.setNextFireTime(MONDAY_08_00);

            assertThat(calculator.isDue(reminder, MONDAY_08_00)).isTrue();
            assertThat(calculator.isDue(reminder, MONDAY_08_00.plusSeconds(1))).isTrue();
            assertThat(calculator.isDue(reminder, MONDAY_08_00.minusSeconds(1))).isFalse();
        }

        @Test
        void isNeverDueWithoutAFireTime() {
            assertThat(calculator.isDue(new Reminder(), MONDAY_08_00)).isFalse();
        }
    }
}

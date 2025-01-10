/*
 * (C) Copyright 2024 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Antoine Taillefer
 */
package org.nuxeo.common.utils;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * @since 2025.0
 */
public class RetryUtils {

    private RetryUtils() {
        // utility class
    }

    /**
     * @see #exponentialBackoff(Supplier, int, Duration, Duration)
     */
    public static <T> T exponentialBackoff(Supplier<T> supplier, int tryCount, Duration timeSlot) {
        return exponentialBackoff(supplier, tryCount, timeSlot, null);
    }

    /**
     * Tries at most {@code tryCount} times to return the result of the given {@code supplier}, if not null. The waiting
     * time between each try is computed with an exponential backoff algorithm based on the given {@code timeSlot}
     * duration.
     * <p>
     * If {@code threshold} is not null, then the waiting time doesn't exceed the {@code threshold} duration: when the
     * waiting time reaches {@code threshold}, it is reset to {@code timeSlot}.
     *
     * @throws RuntimeException if the retry count was exceeded
     */
    public static <T> T exponentialBackoff(Supplier<T> supplier, int tryCount, Duration timeSlot, Duration threshold) {
        long sleepDuration = timeSlot.toMillis();
        for (int i = 0; i < tryCount; i++) {
            T result = supplier.get();
            if (result != null) {
                return result;
            }
            try {
                Thread.sleep(sleepDuration);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(); // NOSONAR
            }
            sleepDuration *= 2; // exponential backoff
            sleepDuration += System.nanoTime() % 4; // random jitter
            if (threshold != null && sleepDuration >= threshold.toMillis()) {
                sleepDuration = timeSlot.toMillis();
            }
        }
        throw new RuntimeException("Exponential backoff try count exceeded"); // NOSONAR
    }

}

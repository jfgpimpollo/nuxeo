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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.nuxeo.common.utils.RetryUtils.exponentialBackoff;

import java.time.Duration;

import org.junit.Test;

/**
 * @since 2025.0
 */
public class TestRetryUtils {

    @Test(timeout = 500)
    public void testExponentialBackoffFailure() {
        // try 8 times with a time slot of 1 ms and never exceed a waiting time of 25 ms
        // should not last more than 8 x 25 = 200 ms
        assertThrows(RuntimeException.class,
                () -> exponentialBackoff(() -> null, 8, Duration.ofMillis(1), Duration.ofMillis(25)));
    }

    @Test
    public void testExponentialBackoffSuccess() {
        assertEquals("foo", exponentialBackoff(() -> "foo", 2, Duration.ofMillis(1)));
    }

}

/*
 * Copyright (c) 2024 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.fluss.memory;

import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.alibaba.fluss.utils.function.ThrowingRunnable.unchecked;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Test for {@link com.alibaba.fluss.memory.LazyMemorySegmentPool}. */
public class LazyMemorySegmentPoolTest {

    @Test
    void testNextSegmentWaiter() throws Exception {
        LazyMemorySegmentPool source = buildLazyMemorySegmentSource(10, 64);
        assertThat(source.pageSize()).isEqualTo(64);
        assertThat(source.freePages()).isEqualTo(10);

        MemorySegment ms1 = source.nextSegment();
        assertThat(source.freePages()).isEqualTo(9);

        MemorySegment ms2 = source.nextSegment();
        assertThat(source.freePages()).isEqualTo(8);

        for (int i = 0; i < 8; i++) {
            source.nextSegment();
        }
        assertThat(source.freePages()).isEqualTo(0);

        assertThatThrownBy(source::nextSegment)
                .isInstanceOf(EOFException.class)
                .hasMessage(
                        "Failed to allocate new segment within the configured max blocking time 100 ms. "
                                + "Total memory: 640 bytes. Page size: 64 bytes. Available pages: 0. Request pages: 1");

        CountDownLatch returnAllLatch = asyncReturnAll(source, Arrays.asList(ms1, ms2));
        CountDownLatch getNextSegmentLatch = asyncGetNextSegment(source);
        assertThat(getNextSegmentLatch.getCount()).isEqualTo(1);
        returnAllLatch.countDown();
        assertThat(getNextSegmentLatch.await(Long.MAX_VALUE, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void testIllegalArgument() {
        assertThatThrownBy(() -> buildLazyMemorySegmentSource(0, 64))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("MaxPages for LazyMemorySegmentPool should be greater than 0.");
        assertThatThrownBy(() -> buildLazyMemorySegmentSource(10, 32 * 1024 * 1024))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Page size should be less than PER_REQUEST_MEMORY_SIZE. "
                                + "Page size is: 32768 KB, PER_REQUEST_MEMORY_SIZE is 16384 KB.");
        assertThatThrownBy(() -> buildLazyMemorySegmentSource(10, 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Page size should be greater than 64 bytes to include the record batch header, but is 30 bytes.");

        LazyMemorySegmentPool lazyMemorySegmentPool = buildLazyMemorySegmentSource(10, 100);
        assertThatThrownBy(
                        () ->
                                lazyMemorySegmentPool.returnAll(
                                        Arrays.asList(
                                                MemorySegment.allocateHeapMemory(100),
                                                MemorySegment.allocateHeapMemory(100))))
                .hasMessage("Return too more memories.");
    }

    private LazyMemorySegmentPool buildLazyMemorySegmentSource(int maxPages, int pageSize) {
        return new LazyMemorySegmentPool(maxPages, pageSize, 100);
    }

    private CountDownLatch asyncReturnAll(
            LazyMemorySegmentPool source, List<MemorySegment> segments) {
        CountDownLatch latch = new CountDownLatch(1);
        Thread thread =
                new Thread(
                        unchecked(
                                () -> {
                                    latch.await();
                                    source.returnAll(segments);
                                }));
        thread.start();
        return latch;
    }

    private CountDownLatch asyncGetNextSegment(LazyMemorySegmentPool source) {
        final CountDownLatch completed = new CountDownLatch(1);
        Thread thread =
                new Thread(
                        () -> {
                            try {
                                try {
                                    source.nextSegment();
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            } finally {
                                completed.countDown();
                            }
                        });
        thread.start();
        return completed;
    }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.fluss.utils;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;

import static org.assertj.core.api.Assertions.assertThat;

/** Test for {@link com.alibaba.fluss.utils.TemporaryClassLoaderContext}. */
public class TemporaryClassLoaderContextTest {

    @Test
    void testTemporaryClassLoaderContext() {
        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

        final URLClassLoader temporaryClassLoader =
                new URLClassLoader(new URL[0], contextClassLoader);

        try (TemporaryClassLoaderContext ignored =
                TemporaryClassLoaderContext.of(temporaryClassLoader)) {
            assertThat(Thread.currentThread().getContextClassLoader())
                    .isEqualTo(temporaryClassLoader);
        }

        assertThat(Thread.currentThread().getContextClassLoader()).isEqualTo(contextClassLoader);
    }
}

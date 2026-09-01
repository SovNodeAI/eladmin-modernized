/*
 *  Copyright 2019-2025 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package me.zhengjie.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 异步线程池生命周期测试：@Async 执行器必须是 Spring 管理的 bean，
 * 上下文关闭后 el-async 工作线程必须退出，打包后的 JVM 才能干净停止。
 *
 * @author Zheng Jie
 */
@org.junit.jupiter.api.Tag("behavioral")
class AsyncExecutorLifecycleTest {

    private static final long TERMINATION_TIMEOUT_MILLIS = 10_000L;

    @Test
    void contextCloseShutsDownTheAsyncExecutorAndItsWorkerThreads() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "task.pool.core-pool-size", "5",
                "task.pool.max-pool-size", "10",
                "task.pool.keep-alive-seconds", "60",
                "task.pool.queue-capacity", "50")));
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setEnvironment(environment);
        context.register(AsyncExecutor.class);
        context.refresh();
        ExecutorService service;
        try {
            Executor asyncExecutor = context.getBean(AsyncExecutor.class).getAsyncExecutor();
            assertSame(context.getBean("elAsync", ExecutorService.class), asyncExecutor,
                    "the @Async executor must be the Spring managed bean, not an ad hoc instance");
            service = (ExecutorService) asyncExecutor;
            assertFalse(service.isShutdown());
            service.submit(() -> { }).get(10, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            context.close();
        }
        assertTrue(service.isShutdown(), "context close must shut the async executor down");

        long deadline = System.currentTimeMillis() + TERMINATION_TIMEOUT_MILLIS;
        while (!service.isTerminated() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50L);
        }
        assertTrue(service.isTerminated(), "async worker threads must terminate after context close");

        deadline = System.currentTimeMillis() + TERMINATION_TIMEOUT_MILLIS;
        while (liveAsyncWorkerCount() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50L);
        }
        assertTrue(liveAsyncWorkerCount() == 0,
                "no el-async worker thread may outlive the Spring context, found " + liveAsyncWorkerCount());
    }

    private static int liveAsyncWorkerCount() {
        int alive = 0;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && thread.getName().startsWith("el-async-")) {
                alive++;
            }
        }
        return alive;
    }
}

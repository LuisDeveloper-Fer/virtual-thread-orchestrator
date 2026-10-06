package dev.portfolio;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import dev.portfolio.orchestration.QuoteService;
import dev.portfolio.orchestration.QuoteService.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

class QuoteServiceTest {
  HttpServer server;
  ExecutorService handlers;
  QuoteService service;
  AtomicInteger active = new AtomicInteger(), peak = new AtomicInteger();
  volatile CountDownLatch entered = new CountDownLatch(0), release = new CountDownLatch(0);

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    handlers = Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(handlers);
    server.createContext(
        "/quote",
        exchange -> {
          int current = active.incrementAndGet();
          peak.accumulateAndGet(current, Math::max);
          try {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            if (exchange.getRequestURI().getQuery().contains("NEVER")) Thread.sleep(5000);
            exchange.sendResponseHeaders(
                exchange.getRequestURI().getQuery().contains("ERROR") ? 500 : 200, -1);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            active.decrementAndGet();
            exchange.close();
          }
        });
    server.start();
    service =
        new QuoteService(
            "http://127.0.0.1:" + server.getAddress().getPort(), 1500, new SimpleMeterRegistry());
  }

  @AfterEach
  void stop() {
    release.countDown();
    service.close();
    server.stop(0);
    handlers.shutdownNow();
  }

  @Test
  void usesActualVirtualThreads() throws Exception {
    assertThat(service.query(Mode.VIRTUAL, Scenario.SUCCESS, 4).results())
        .hasSize(4)
        .allSatisfy(
            o -> {
              assertThat(o.status()).isEqualTo("SUCCESS");
              assertThat(o.virtualThread()).isTrue();
            });
  }

  @Test
  void platformComparisonUsesPlatformThreads() throws Exception {
    assertThat(service.query(Mode.PLATFORM, Scenario.SUCCESS, 3).results())
        .allSatisfy(
            o -> {
              assertThat(o.status()).isEqualTo("SUCCESS");
              assertThat(o.virtualThread()).isFalse();
            });
  }

  @Test
  void reportsProviderErrors() throws Exception {
    assertThat(service.query(Mode.VIRTUAL, Scenario.ERROR, 2).results())
        .extracting(Outcome::status)
        .containsOnly("HTTP_ERROR");
  }

  @Test
  void deadlineCancelsAndPermitsRecover() throws Exception {
    var batch = service.query(Mode.VIRTUAL, Scenario.NEVER, 12);
    assertThat(batch.elapsedMs()).isLessThan(3500);
    assertThat(batch.results())
        .extracting(Outcome::status)
        .allMatch(s -> s.equals("TIMEOUT") || s.equals("CANCELLED"));
    assertThat(service.query(Mode.VIRTUAL, Scenario.SUCCESS, 8).results())
        .extracting(Outcome::status)
        .containsOnly("SUCCESS");
  }

  @Test
  void boundsDownstreamAndRejectsNinthBatch() throws Exception {
    service.close();
    service =
        new QuoteService(
            "http://127.0.0.1:" + server.getAddress().getPort(), 4000, new SimpleMeterRegistry());
    entered = new CountDownLatch(8);
    release = new CountDownLatch(1);
    try (var callers = Executors.newVirtualThreadPerTaskExecutor()) {
      var tasks = new java.util.ArrayList<Future<Batch>>();
      for (int i = 0; i < 8; i++)
        tasks.add(callers.submit(() -> service.query(Mode.VIRTUAL, Scenario.SUCCESS, 1)));
      assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
      assertThatThrownBy(() -> service.query(Mode.VIRTUAL, Scenario.SUCCESS, 1))
          .isInstanceOf(CapacityException.class);
      assertThat(peak.get()).isLessThanOrEqualTo(8);
      release.countDown();
      for (var task : tasks)
        assertThat(task.get(3, TimeUnit.SECONDS).results().getFirst().status())
            .isEqualTo("SUCCESS");
    }
  }
}

package dev.portfolio.orchestration;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class QuoteService {
  public enum Mode {
    VIRTUAL,
    PLATFORM
  }

  public enum Scenario {
    SUCCESS,
    SLOW,
    ERROR,
    NEVER
  }

  public record Outcome(int provider, String status, Boolean virtualThread) {}

  public record Batch(String correlationId, Mode mode, long elapsedMs, List<Outcome> results) {}

  public static class CapacityException extends RuntimeException {}

  private final ExecutorService virtual = Executors.newVirtualThreadPerTaskExecutor();
  private final ExecutorService platform =
      new ThreadPoolExecutor(8, 8, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(96));
  private final Semaphore admission = new Semaphore(8), downstream = new Semaphore(8, true);
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofMillis(300)).build();
  private final String url;
  private final long deadlineMs;
  private final MeterRegistry metrics;

  public QuoteService(
      @Value("${provider.url:http://localhost:9096}") String url,
      @Value("${provider.deadline-ms:1500}") long deadlineMs,
      MeterRegistry metrics) {
    this.url = url;
    this.deadlineMs = deadlineMs;
    this.metrics = metrics;
    metrics.gauge("quotes.active.calls", downstream, s -> 8 - s.availablePermits());
  }

  public Batch query(Mode mode, Scenario scenario, int count) throws InterruptedException {
    if (!admission.tryAcquire()) {
      metrics.counter("quotes.rejected").increment();
      throw new CapacityException();
    }
    long start = System.nanoTime(), deadline = start + TimeUnit.MILLISECONDS.toNanos(deadlineMs);
    String correlation = UUID.randomUUID().toString();
    List<Future<Outcome>> futures = new ArrayList<>();
    try {
      ExecutorService executor = mode == Mode.VIRTUAL ? virtual : platform;
      for (int i = 0; i < count; i++) {
        final int index = i;
        futures.add(executor.submit(() -> call(index, scenario, deadline, correlation)));
      }
      List<Outcome> results = new ArrayList<>();
      for (int i = 0; i < futures.size(); i++) {
        var future = futures.get(i);
        try {
          results.add(
              future.isDone()
                  ? future.get()
                  : future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
        } catch (TimeoutException e) {
          future.cancel(true);
          results.add(new Outcome(i, "TIMEOUT", null));
        } catch (ExecutionException e) {
          throw new IllegalStateException("Unexpected provider task failure", e.getCause());
        }
      }
      results.forEach(
          o ->
              metrics
                  .counter("quotes.results", "mode", mode.name(), "status", o.status())
                  .increment());
      return new Batch(
          correlation,
          mode,
          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start),
          List.copyOf(results));
    } finally {
      futures.forEach(
          f -> {
            if (!f.isDone()) f.cancel(true);
          });
      admission.release();
      metrics
          .timer("quotes.batch.duration", "mode", mode.name())
          .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }
  }

  private Outcome call(int index, Scenario scenario, long deadline, String correlation) {
    boolean acquired = false;
    String status;
    try {
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0 || !(acquired = downstream.tryAcquire(remaining, TimeUnit.NANOSECONDS)))
        return new Outcome(index, "TIMEOUT", Thread.currentThread().isVirtual());
      remaining = deadline - System.nanoTime();
      if (remaining <= 0) return new Outcome(index, "TIMEOUT", Thread.currentThread().isVirtual());
      var request =
          HttpRequest.newBuilder(URI.create(url + "/quote?scenario=" + scenario))
              .timeout(Duration.ofNanos(remaining))
              .header("X-Correlation-ID", correlation)
              .GET()
              .build();
      int code = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
      status = code >= 200 && code < 300 ? "SUCCESS" : "HTTP_ERROR";
    } catch (HttpTimeoutException e) {
      status = "TIMEOUT";
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      status = "CANCELLED";
    } catch (java.io.IOException e) {
      status = "IO_ERROR";
    } finally {
      if (acquired) downstream.release();
    }
    return new Outcome(index, status, Thread.currentThread().isVirtual());
  }

  @PreDestroy
  public void close() {
    virtual.shutdownNow();
    platform.shutdownNow();
    client.shutdownNow();
  }
}

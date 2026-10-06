package dev.portfolio.api;

import dev.portfolio.orchestration.QuoteService;
import dev.portfolio.orchestration.QuoteService.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quotes")
public class QuoteController {
  public record Request(
      @NotNull Mode mode, @NotNull Scenario scenario, @Min(1) @Max(12) int providers) {}

  private final QuoteService service;

  public QuoteController(QuoteService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<Batch> query(@Valid @RequestBody Request request)
      throws InterruptedException {
    var batch = service.query(request.mode(), request.scenario(), request.providers());
    return ResponseEntity.ok().header("X-Correlation-ID", batch.correlationId()).body(batch);
  }

  @ExceptionHandler(CapacityException.class)
  ResponseEntity<ProblemDetail> capacity() {
    return ResponseEntity.status(429)
        .header("Retry-After", "2")
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS, "Hay 8 consultas activas. Intenta más tarde."));
  }
}

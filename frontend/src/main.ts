import { Component, signal } from "@angular/core";
import { bootstrapApplication } from "@angular/platform-browser";
import { FormsModule } from "@angular/forms";
import { JsonPipe } from "@angular/common";
import { HttpClient, provideHttpClient } from "@angular/common/http";
interface Outcome {
  provider: number;
  status: string;
  virtualThread: boolean | null;
}
interface Batch {
  correlationId: string;
  mode: string;
  elapsedMs: number;
  results: Outcome[];
}
@Component({
  selector: "app-root",
  standalone: true,
  imports: [FormsModule, JsonPipe],
  templateUrl: "./app.html",
})
class App {
  readonly demo =
    location.hostname.endsWith(".github.io") ||
    new URLSearchParams(location.search).get("demo") === "true";
  readonly busy = signal(false);
  readonly result = signal<Batch | null>(null);
  readonly error = signal("");
  mode = "VIRTUAL";
  scenario = "SUCCESS";
  providers = 6;
  constructor(private http: HttpClient) {}
  run() {
    if (this.busy()) return;
    this.error.set("");
    if (
      !Number.isInteger(this.providers) ||
      this.providers < 1 ||
      this.providers > 12
    ) {
      this.error.set("Elige entre 1 y 12 proveedores.");
      return;
    }
    this.busy.set(true);
    this.result.set(null);
    const request = {
      mode: this.mode,
      scenario: this.scenario,
      providers: this.providers,
    };
    if (this.demo) {
      setTimeout(() => {
        this.result.set({
          correlationId: crypto.randomUUID(),
          mode: request.mode,
          elapsedMs: 0,
          results: Array.from({ length: request.providers }, (_, provider) => ({
            provider,
            status:
              request.scenario === "SUCCESS"
                ? "SUCCESS"
                : request.scenario === "ERROR"
                  ? "HTTP_ERROR"
                  : "TIMEOUT",
            virtualThread: null,
          })),
        });
        this.busy.set(false);
      }, 900);
    } else
      this.http.post<Batch>("/api/quotes", request).subscribe({
        next: (batch) => {
          this.result.set(batch);
          this.busy.set(false);
        },
        error: (e) => {
          this.error.set(
            e.status === 429
              ? "Capacidad ocupada. Intenta de nuevo en unos segundos."
              : "No se completó la consulta. Revisa que la API y el simulador estén activos.",
          );
          this.busy.set(false);
        },
      });
  }
}
bootstrapApplication(App, { providers: [provideHttpClient()] }).catch(
  console.error,
);

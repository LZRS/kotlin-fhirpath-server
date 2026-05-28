# kotlin-fhirpath-server

A Ktor-based server that evaluates [FHIRPath](https://hl7.org/fhirpath/) expressions against FHIR
resources. It implements the
[FHIRPath Lab Server Engine API](https://github.com/brianpos/fhirpath-lab/blob/master/server-api.md)
specification, supporting FHIR versions **R4**, **R4B**, and **R5**.

## Prerequisites

- **Java 21** (the project uses JVM toolchain 21)
- **Gradle** (wrapper included — no separate installation needed)

## Building & Running Locally

Use the Gradle wrapper to build and run the server:

| Task | Description |
|------|-------------|
| `./gradlew run` | Run the server locally |
| `./gradlew test` | Run the test suite |
| `./gradlew build` | Compile and assemble the project |
| `./gradlew buildFatJar` | Build a self-contained executable JAR (`fhirpath-server.jar`) |

The server starts on port `8080` by default. Set the `PORT` environment variable to override:

```bash
PORT=9090 ./gradlew run
```

When the server starts successfully you will see:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

### Running the fat JAR directly

```bash
./gradlew buildFatJar

java -jar build/libs/fhirpath-server.jar
```

## API

The server exposes three FHIRPath evaluation endpoints — one per FHIR version — plus a health check:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/` | GET | API overview and endpoint listing |
| `/health` | GET | Health check with current timestamp |
| `/fhir/$fhirpath` | POST | Evaluate a FHIRPath expression against an **R4** resource |
| `/fhir/$fhirpath-r4b` | POST | Evaluate a FHIRPath expression against an **R4B** resource |
| `/fhir/$fhirpath-r5` | POST | Evaluate a FHIRPath expression against an **R5** resource |

### Request

**Content-Type**: `application/fhir+json` or `application/json`

**Body**: A FHIR `Parameters` resource with the following parameters:

| Parameter | Type | Required | Description |
|-----------|------|:--------:|-------------|
| `expression` | string | Yes | FHIRPath expression to evaluate |
| `resource` | Resource | Yes | FHIR resource to evaluate against |
| `context` | string | No | FHIRPath expression to set the evaluation scope |
| `variables` | multi-part | No | Named variables passed into the expression |

**Example request body:**

```json
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "expression",
      "valueString": "name.family"
    },
    {
      "name": "resource",
      "resource": {
        "resourceType": "Patient",
        "name": [{ "family": "Smith", "given": ["John"] }]
      }
    }
  ]
}
```

### Response

A successful evaluation returns HTTP `200` with a FHIR `Parameters` resource containing the results
and debug trace information.

Validation errors return HTTP `400` with an `OperationOutcome`. Unexpected server errors return HTTP
`500` with an `OperationOutcome`.

## Deployment

### Docker

The Ktor Gradle plugin provides built-in Docker support:

| Task | Description |
|------|-------------|
| `./gradlew buildImage` | Build a Docker image from the fat JAR |
| `./gradlew publishImageToLocalRegistry` | Publish the image to the local Docker registry |
| `./gradlew runDocker` | Build the image and run it as a container |

To run the published image manually:

```bash
docker run -p 8080:8080 kotlin-fhirpath-server:0.0.1
```

Override the port via the `PORT` environment variable:

```bash
docker run -e PORT=9090 -p 9090:9090 kotlin-fhirpath-server:0.0.1
```

### Deploying the fat JAR

Copy `build/libs/fhirpath-server.jar` to any host with Java 21 and run:

```bash
java -jar fhirpath-server.jar
```

The server does not require any external dependencies or database — all state is in-process.

## Specification

This server implements the
[FHIRPath Lab Server Engine API](https://github.com/brianpos/fhirpath-lab/blob/master/server-api.md).
It is compatible with the
[FHIRPath Lab](https://fhirpath-lab.com) UI as a configurable server-side evaluation engine.

## License

Apache License 2.0 — see [LICENSE](LICENSE).

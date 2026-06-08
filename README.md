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

|          Task           |                          Description                          |
|-------------------------|---------------------------------------------------------------|
| `./gradlew run`         | Run the server locally                                        |
| `./gradlew test`        | Run the test suite                                            |
| `./gradlew build`       | Compile and assemble the project                              |
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

|    Endpoint     | Method |                        Description                         |
|-----------------|--------|------------------------------------------------------------|
| `/`             | GET    | API overview and endpoint listing                          |
| `/health`       | GET    | Health check with current timestamp                        |
| `/fhirpath-r4`  | POST   | Evaluate a FHIRPath expression against an **R4** resource  |
| `/fhirpath-r4b` | POST   | Evaluate a FHIRPath expression against an **R4B** resource |
| `/fhirpath-r5`  | POST   | Evaluate a FHIRPath expression against an **R5** resource  |

### Request

**Content-Type**: `application/fhir+json` or `application/json`

**Body**: A FHIR `Parameters` resource. See the
[input parameters definition](https://github.com/brianpos/fhirpath-lab/blob/master/server-api.md#input-parameters-resource)
for the full parameter specification. Support status in this implementation:

| Parameter           | Supported |
|---------------------|:---------:|
| `expression`        |     ✅     |
| `resource`          |     ✅     |
| `context`           |     ✅     |
| `variables`         |     ✅     |
| `terminologyserver` |     ❌     |

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

The server is deployed to a **Google Cloud Compute Engine** VM. Deployment is currently manual —
there is no CI/CD pipeline.

### What you need before deploying

- Access to the GCP project with appropriate IAM permissions (Compute Instance Admin is sufficient
  for deployments)
- [Google Cloud CLI](https://cloud.google.com/sdk/docs/install) installed and authenticated:

  ```bash
  gcloud auth login
  gcloud config set project <PROJECT_ID>
  ```

- Java 21 installed on the VM. If not present, connect to the VM and install it:

  ```bash
  gcloud compute ssh <INSTANCE_NAME> --zone <ZONE>
  # on the VM:
  sudo apt-get install -y temurin-21-jdk
  ```

### Build

Build the self-contained fat JAR locally:

```bash
./gradlew buildFatJar
# produces build/libs/fhirpath-server.jar
```

### Copy to the VM

```bash
gcloud compute scp build/libs/fhirpath-server.jar <INSTANCE_NAME>:~/fhirpath-server.jar --zone <ZONE>
```

### Run on the VM

Connect and start the server:

```bash
gcloud compute ssh <INSTANCE_NAME> --zone <ZONE>
# on the VM:
java -jar ~/fhirpath-server.jar
```

The `PORT` environment variable controls which port the server binds to (default `8080`). Make sure
the VM's firewall allows inbound traffic on that port.

### Running as a system service

To keep the server running after disconnecting and have it restart automatically on VM reboot, create
a systemd unit:

```bash
sudo tee /etc/systemd/system/fhirpath-server.service > /dev/null <<EOF
[Unit]
Description=Kotlin FHIRPath Server
After=network.target

[Service]
ExecStart=/usr/bin/java -jar /home/<VM_USER>/fhirpath-server.jar
Restart=always
Environment=PORT=8080

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable fhirpath-server
sudo systemctl start fhirpath-server
```

Check service status with `sudo systemctl status fhirpath-server` and logs with
`sudo journalctl -u fhirpath-server -f`.

### Deploying an update

```bash
# 1. Build locally
./gradlew buildFatJar

# 2. Copy to VM
gcloud compute scp build/libs/fhirpath-server.jar <INSTANCE_NAME>:~/fhirpath-server.jar --zone <ZONE>

# 3. Restart the service
gcloud compute ssh <INSTANCE_NAME> --zone <ZONE> --command "sudo systemctl restart fhirpath-server"
```

### Docker (local / experimental)

The Ktor Gradle plugin also provides Docker tasks for local experimentation:

|                  Task                   |                  Description                   |
|-----------------------------------------|------------------------------------------------|
| `./gradlew buildImage`                  | Build a Docker image from the fat JAR          |
| `./gradlew publishImageToLocalRegistry` | Publish the image to the local Docker registry |
| `./gradlew runDocker`                   | Build the image and run it as a container      |

```bash
docker run -p 8080:8080 kotlin-fhirpath-server:1.0.0
```

## Specification

This server implements the
[FHIRPath Lab Server Engine API](https://github.com/brianpos/fhirpath-lab/blob/master/server-api.md).

Once deployed, you can point [FHIRPath Lab](https://fhirpath-lab.com) to this server as its
evaluation engine. In FHIRPath Lab, open **Settings → Engine** and enter the base URL of your
deployed instance (e.g. `http://<INSTANCE_IP>:8080`).

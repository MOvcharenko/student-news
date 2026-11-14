# avatar-service

The starter for **Homework 12**. A small Spring Boot service that stores one picture per id on disk.
The Student News UI uses it for the picture on each news post, stored under the id `news-<post id>`.

The service is finished and tested. What it does not have yet is a container: that is the homework
(see [Containerise it](#containerise-it-homework-12)).

---

## Endpoints

| Method | Path | Success | Errors |
|--------|------|---------|--------|
| `POST` | `/avatars/{id}` | `201` with `id`, `url`, `size`, `content_type` | `400` bad id or no `file` field, `413` too large, `415` not PNG/JPEG, `500` cannot store |
| `GET` | `/avatars/{id}` | `200` with the image and its `Content-Type` | `400` bad id, `404` no avatar |
| `DELETE` | `/avatars/{id}` | `204` | `400` bad id, `404` no avatar |
| `GET` | `/health` | `200` `{"status":"ok"}` | `503` `{"status":"error","detail":"..."}` |

- An `id` is 1 to 64 characters from `A-Z`, `a-z`, `0-9`, `_` and `-`.
- Uploads are multipart, with the image in the form field `file`. Only PNG and JPEG are accepted, and
  the service checks the file's first bytes, not the content type the client claims.
- A new upload replaces the earlier avatar for that id, whichever format it was in. It is stored as
  `<UPLOAD_DIR>/<id>.png` or `<UPLOAD_DIR>/<id>.jpg`.
- `/health` returns `200` only when `UPLOAD_DIR` exists and is writable. Otherwise `detail` names the
  directory and says what is wrong, for example that it is missing or that permission is denied.
- Every response except the image itself is JSON. Errors look like
  `{"error": "Not Found", "detail": "no avatar stored for id news-7"}`.

```bash
# Health
curl http://localhost:8090/health

# Upload a picture for news post 7
curl -F "file=@picture.png" http://localhost:8090/avatars/news-7
# {"id":"news-7","url":"/avatars/news-7","size":5120,"content_type":"image/png"}

# Download it
curl -o downloaded.png http://localhost:8090/avatars/news-7

# Delete it
curl -X DELETE http://localhost:8090/avatars/news-7
```

---

## Configuration

Environment variables only. Each one has a default.

| Variable | Default | Meaning |
|----------|---------|---------|
| `PORT` | `8090` | Port the service listens on |
| `UPLOAD_DIR` | `/data/avatars` | Directory the images are stored in |
| `MAX_BYTES` | `2097152` (2 MiB) | Largest accepted image, in bytes |

At startup the service tries to create `UPLOAD_DIR`. If it cannot, it logs a warning and keeps running:
`/health` then returns `503` with the reason, and uploads return `500`. Read the `/health` detail
first when something is wrong.

The service logs to stdout only: a few lines at startup, then one line per request.

---

## How the news UI uses it

The UI calls the service from the browser at `http://localhost:8090`; at frontend build time,
`VITE_AVATAR_URL` changes that. When the service is not reachable or `/health` is not `200`, the UI
disables the picture field and shows why. Everything else in the news app works as before.

---

## Run it locally (without Docker)

You need JDK 27 and Maven 3.9 or newer.

```bash
cd avatar-service
mvn package                     # runs the tests and builds target/avatar-service.jar
UPLOAD_DIR="$PWD/data/avatars" java -jar target/avatar-service.jar
```

The service listens on <http://localhost:8090>. `data/` is in `.gitignore`. To use another port or limit:

```bash
PORT=9000 MAX_BYTES=500000 UPLOAD_DIR="$PWD/data/avatars" java -jar target/avatar-service.jar
```

On Windows PowerShell, set the variables first, for example `$env:UPLOAD_DIR = "$PWD\data\avatars"`.

---

## Tests

```bash
cd avatar-service
mvn test
```

The tests cover every endpoint and error case, configuration from the environment, the request log,
and what happens when `UPLOAD_DIR` is missing or not writable. The tests for an unwritable directory
are skipped when run as root, because root can write anywhere. CI runs them in the
"Avatar service · tests" job.

### Smoke test

`smoke-test.sh` checks a running service from the outside with nothing but bash and curl. It checks
health, uploads a tiny generated PNG, downloads it and compares the bytes, deletes it and confirms
the `404`:

```bash
./smoke-test.sh                                   # http://localhost:8090
BASE_URL=http://localhost:9000 ./smoke-test.sh    # anywhere else
```

It stops at the first failure, says what failed, and exits non-zero.

---

## Containerise it (Homework 12)

Provide:

1. **A `Dockerfile`** in `avatar-service/`. The file is already there, and empty.
2. **A `.dockerignore`** in `avatar-service/`.
3. **An `avatar-service` service in `docker-compose.yml`**, next to `db`, `backend` and `frontend`.

You are done when, after `docker compose up --build`:

- `./smoke-test.sh` passes against the container,
- `curl http://localhost:8090/health` returns `{"status":"ok"}`, and
- the news UI at <http://localhost:5173> offers the picture field and shows the pictures you add.

If `/health` returns `503`, its `detail` says which directory is the problem and why.

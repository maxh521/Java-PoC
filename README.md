# Tiny URL Shortener

A minimal REST API for shortening URLs, built with [FastAPI](https://fastapi.tiangolo.com/).

## Why this project

Small on purpose — it's meant to show clean API design in a compact footprint:

- RESTful endpoints with proper status codes
- Request/response validation via Pydantic models
- Idempotent shortening (same URL → same code)
- Click tracking per short code
- A base62 encoder for compact, readable codes
- Test coverage with `pytest` + `TestClient`

## Endpoints

| Method | Path            | Description                          |
|--------|-----------------|---------------------------------------|
| POST   | `/shorten`      | Create a short code for a URL         |
| GET    | `/{code}`       | Redirect to the original URL          |
| GET    | `/stats/{code}` | View click count and metadata         |

## Quickstart

```bash
pip install -r requirements.txt
uvicorn main:app --reload
```

Then open http://127.0.0.1:8000/docs for interactive Swagger docs.

### Example

```bash
curl -X POST http://127.0.0.1:8000/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com/some/very/long/path"}'
# {"code": "0", "short_url": "/0", "original_url": "https://example.com/some/very/long/path"}

curl -L http://127.0.0.1:8000/0
# redirects to the original URL
```

## Running tests

```bash
pytest
```

## Notes

The data store here is a plain in-memory dict, intentionally — this project is meant to
demonstrate API design, not persistence. Swapping in Redis or Postgres would be a
natural next step for production use.

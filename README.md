# CodePilot AI

An AI software engineering assistant that answers questions about a codebase using the
code itself, and proposes changes as reviewable pull requests.

Sign in with GitHub, point it at one of your repositories, and ask things like *"how is
authentication handled?"* — the answer cites the actual files it read. Ask for a change
and you get a pull request, not a chat message you have to copy out by hand.

🔗 **Live demo:** https://codepilot-ai.onrender.com
*(Hosted on a free tier — the first request after a quiet period takes ~50 seconds to
wake the container.)*

---

## What it does

| | |
|---|---|
| **Ask about your code** | Retrieval-augmented answers grounded in your repository, with the source files cited |
| **Find usages / dependencies / impact** | Symbol-level analysis from the AST, separate from semantic search |
| **Propose changes** | Plan → preview the diff → approve → pull request opened on your behalf |
| **Bring your own repositories** | GitHub OAuth; public and private repos both work, cloned with your own token |

---

## Architecture

```
Angular 18 (served by Spring Boot, one origin)
        │
        ▼
Spring Boot 4 / Java 21
        │
        ├── Retrieval ──► pgvector (Postgres)         semantic search over code chunks
        ├── Symbols   ──► code_symbols / references    AST-resolved declarations and call sites
        ├── Git       ──► JGit + GitHub REST           clone, branch, commit, pull request
        └── Models    ──► Groq (chat) · Jina (embeddings)
```

### Two kinds of search, deliberately not merged

The central idea in the retrieval layer: **semantic search answers "what code is *about*
this?", symbol search answers "what code *touches* this?"** They fail in different ways and
are kept separate.

Vector search will happily return the five nearest chunks to a question about a concept the
repository doesn't contain. Symbol resolution either finds a caller or it doesn't. When the
two are combined for edit planning, results resolved by the type solver are marked
**confirmed** and name-only matches are marked **possible** — never blended, because a
substantial fraction of references in a Spring codebase can't be fully resolved, and
presenting those as certain would make find-usages untrustworthy.

### Indexing pipeline

```
scan → parse (JavaParser AST) → chunk → embed → pgvector
                              └──────► symbol + reference extraction
```

Indexing runs as a background job: `POST /api/v1/indexing` returns a job id immediately and
the client polls. A multi-minute blocking request is killed by most platform proxies, and
the work would finish server-side while the caller saw a gateway timeout.

Measured on a 239-chunk repository: **~41 seconds** end to end with Jina embeddings.

---

## Stack

**Backend** — Java 21, Spring Boot 4, Spring Security (OAuth2), Spring Data JPA, Flyway,
WebFlux (streaming), JavaParser with symbol solving, JGit
**Database** — PostgreSQL 17 + pgvector
**Frontend** — Angular 18, standalone components, signals, SSE streaming
**AI** — Groq (`gpt-oss-120b`) for chat, Jina `jina-code-embeddings-1.5b` for embeddings
**Infra** — Docker (multi-stage), Render, Neon

Model providers sit behind an interface and are chosen by configuration, so swapping one is
a properties change rather than a code change.

---

## Security

- **GitHub OAuth** with an `HttpOnly` session cookie — no passwords stored
- **Access tokens encrypted at rest** (AES-GCM, random IV per encryption). The token grants
  repo read/write on the user's account, so plaintext in a database backup would be a
  serious breach
- **Ownership enforced at the lookup, not just the list.** `findById` is absent from the
  repository interfaces; only `findByIdAndUserId` exists. The id comes from the client, so an
  unscoped lookup is an IDOR — and one that's invisible in testing, because you only ever use
  your own ids
- **Arbitrary-path endpoints gated by configuration, defaulting to off.** Registering a local
  filesystem path is a file-read primitive when publicly reachable; a deployment that forgets
  to configure it is locked down rather than wide open
- Each user's own GitHub token drives clone and pull-request creation, so permissions and
  attribution are correct by construction rather than everyone sharing the server's identity

---

## Running locally

**Prerequisites:** Java 21, Node 22, Docker, a GitHub OAuth app, a Jina API key (free), a
Groq API key (free).

```bash
# 1. Postgres with pgvector
docker run -d --name codepilot-postgres \
  -e POSTGRES_DB=codepilot -e POSTGRES_PASSWORD=postgres \
  -p 3506:5432 pgvector/pgvector:pg17

# 2. Environment
export DATABASE_URL=jdbc:postgresql://localhost:3506/codepilot
export DATABASE_USERNAME=postgres
export DATABASE_PASSWORD=postgres
export JINA_API_KEY=...
export GROQ_API_KEY=...
export GITHUB_CLIENT_ID=...
export GITHUB_CLIENT_SECRET=...
export TOKEN_ENCRYPTION_KEY=$(openssl rand -base64 32)

# 3. Backend
cd backend && mvn spring-boot:run

# 4. Frontend (separate terminal) — the dev proxy forwards /api to :8080
cd frontend && npm install && ng serve
```

GitHub OAuth app callback URL: `http://localhost:4200/login/oauth2/code/github`

Or run the whole thing as it deploys:

```bash
docker compose up --build   # http://localhost:8080
```

---

## Known limitations

Stated plainly, because they're real:

- **Non-Java repositories are coarse.** Java gets method-level chunks and a symbol index;
  everything else is indexed one chunk per file. Retrieval finds the right file, but there's
  no find-usages and large files consume the context budget.
- **No relevance floor.** Similarity search returns the nearest K chunks regardless of
  distance, so a question about something the repository doesn't contain still returns five
  chunks. The answer is marked as ungrounded only when retrieval returns *nothing*, not when
  it returns something irrelevant.
- **Overloaded methods are indistinguishable** — qualified names carry no parameter types.
- **The index is a snapshot.** Nothing watches the filesystem; a repository must be
  re-indexed after it changes.
- **Free-tier constraints.** Groq rate-limits bursts of questions; the host sleeps after 15
  minutes idle; cloned repositories are wiped on restart and re-cloned on demand.

---

## Roadmap

- Relevance threshold so irrelevant context is reported as none
- Incremental re-indexing on file change
- Language-aware parsing beyond Java
- Per-user rate limiting
- Evaluation harness for retrieval quality and answer grounding

---

## Notes

Built as a learning project to work through retrieval-augmented generation, AST-based code
analysis, and the operational side of running an LLM application — rate limits, silent
failure modes, and the difference between an answer that is grounded and one that merely
sounds grounded.

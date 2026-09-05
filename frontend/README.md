# CodePilot AI — Frontend (Phase 1)

An Angular 18 (standalone components + Signals) frontend for CodePilot AI,
an AI-powered software engineering assistant. This is Phase 1: conversations,
message history, and real-time token-by-token streaming chat over the
existing Spring Boot backend.

## Prerequisites

- Node.js 18+ and npm
- The CodePilot AI Spring Boot backend running locally at `http://localhost:8080`
  (see `src/environments/environment.ts` to change this)

## Getting started

```bash
npm install
npm start        # ng serve — http://localhost:4200
```

Make sure the backend is running first; the app will show a
"Unable to connect to CodePilot backend" banner otherwise.

## Scripts

| Command          | Purpose                                            |
|-------------------|-----------------------------------------------------|
| `npm start`        | Run the dev server (`ng serve`)                      |
| `npm run build`     | Production build to `dist/codepilot-ai-frontend`    |
| `npm test`          | Run unit tests (Karma + Jasmine)                     |

## Architecture

```
src/app/
  core/
    models/        API + shared TypeScript interfaces
    services/       ConversationService, MessageService, ChatService (SSE)
    interceptors/   Rewrites /api/... to environment.apiBaseUrl
  features/
    chat/
      components/   chat-page, chat-header, message-list, message-item,
                     message-composer, welcome-screen, typing-indicator
      services/      ChatStateService — single owner of all chat/conversation
                      state, built on Angular Signals
    conversations/
      components/    conversation-sidebar, conversation-item
  shared/
    pipes/          MarkdownPipe (marked + DOMPurify)
    utils/          SseParser — robust chunk-boundary-safe SSE parsing
```

### Streaming design

`POST /api/v1/chat/stream` returns `text/event-stream`, but Angular's
`HttpClient` cannot progressively expose a POST response body. `ChatService`
therefore uses the native `fetch()` API + `ReadableStream` + `TextDecoder`,
feeding decoded text into `SseParser`, which reassembles JSON payloads even
when the network splits them mid-object across multiple `read()` calls (see
`shared/utils/sse-parser.spec.ts` for the exact scenarios this handles).

### State management

Phase 1 uses Angular Signals via a single `ChatStateService`, not NgRx — the
state graph (conversations → selected conversation → its messages → one
in-flight stream) is small and mostly linear. Revisit this if a future phase
needs cross-cutting state (multi-tab sync, undo/redo, etc.).

## Testing

```bash
npm test
```

38 tests cover: SSE parser chunk-fragmentation scenarios, the chat streaming
client (mocked `fetch`), conversation/message HTTP services, and composer
keyboard behavior (Enter vs. Shift+Enter, disabled states).

If no browser is available in your environment, point `CHROME_BIN` at any
installed Chrome/Chromium binary before running `npm test` — see
`karma.conf.js`.

## Not in scope for Phase 1

Per the project brief: RAG, repository indexing, GitHub integration, Kafka,
Redis, AI agents, code analysis, authentication, billing, and team
collaboration are explicitly deferred to future phases.

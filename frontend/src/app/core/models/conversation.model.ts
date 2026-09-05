/**
 * A conversation as returned by the backend.
 * Maps 1:1 to the JSON shape of `POST/GET /api/v1/conversations`.
 */
export interface Conversation {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
}

/** Request body for `POST /api/v1/conversations`. */
export interface CreateConversationRequest {
  title: string;
}

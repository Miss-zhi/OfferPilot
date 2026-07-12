## ADDED Requirements

### Requirement: Save user message
The system SHALL persist user messages immediately upon receiving a chat stream request.

#### Scenario: User sends message via SSE stream
- **WHEN** ChatController receives POST /stream with user message
- **THEN** system saves user message to op_chat_message with role=USER, content=<message text>, seq=<next sequence>

#### Scenario: Session not yet created
- **WHEN** user sends first message without a sessionId
- **THEN** system creates session first, then saves the message

### Requirement: Save AI message
The system SHALL persist AI messages after the SSE stream completes.

#### Scenario: AI reply complete
- **WHEN** frontend receives SSE "done" event
- **THEN** frontend POSTs the complete AI message (content, thinkingContent, toolCalls) to /messages endpoint for persistence

#### Scenario: AI message with thinking content
- **WHEN** AI reply includes thinking process
- **THEN** system saves thinking_content in the message record

#### Scenario: AI message with tool calls
- **WHEN** AI reply involved tool calls
- **THEN** system saves tool_calls as JSON array in the message record

### Requirement: Load session messages
The system SHALL return all messages for a given session in chronological order.

#### Scenario: Load messages for existing session
- **WHEN** frontend requests GET /sessions/{sessionId}/messages
- **THEN** system returns all messages ordered by seq ASC, including content and thinking_content

#### Scenario: Load messages for non-existent session
- **WHEN** frontend requests messages for a session that doesn't exist
- **THEN** system returns empty list

### Requirement: Search messages
The system SHALL support full-text search across user messages and AI replies.

#### Scenario: Search by keyword
- **WHEN** user searches with a keyword via GET /sessions/search?q=<keyword>
- **THEN** system performs FULLTEXT search on content and thinking_content, returns matching sessions with match snippets

#### Scenario: No matching messages
- **WHEN** search keyword matches no messages
- **THEN** system returns empty results

### Requirement: Cascade delete messages on session delete
The system SHALL delete all messages when their parent session is deleted.

#### Scenario: Delete session
- **WHEN** user deletes a session
- **THEN** all messages associated with that session are also deleted

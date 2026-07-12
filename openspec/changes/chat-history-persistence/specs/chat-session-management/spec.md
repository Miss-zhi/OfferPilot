## ADDED Requirements

### Requirement: Session list display
The system SHALL display current user's session list sorted by updated_at descending.

#### Scenario: User opens chat page
- **WHEN** user navigates to chat page
- **THEN** system loads and displays session list in sidebar, with most recently updated session first

#### Scenario: Empty session list
- **WHEN** user has no previous sessions
- **THEN** system displays "新对话" button only, no session list

### Requirement: Create new session
The system SHALL allow user to create a new empty session.

#### Scenario: Create session before first message
- **WHEN** user clicks "新对话" button
- **THEN** system creates a new session with auto-generated sessionId and switches to it

### Requirement: Switch session
The system SHALL allow user to switch between existing sessions and restore full message history.

#### Scenario: Click session in sidebar
- **WHEN** user clicks a session in the sidebar
- **THEN** system loads all messages for that session and displays them in the chat area

#### Scenario: Current session highlighted
- **WHEN** a session is active
- **THEN** it SHALL be visually highlighted in the sidebar

### Requirement: Delete session
The system SHALL allow user to delete a session and all its messages.

#### Scenario: Delete from sidebar
- **WHEN** user clicks delete on a session and confirms
- **THEN** system deletes the session and all associated messages, removes it from the list

#### Scenario: Delete current session
- **WHEN** user deletes the currently active session
- **THEN** system switches to the most recent remaining session or shows empty state

### Requirement: Rename session
The system SHALL allow user to rename a session title.

#### Scenario: Edit title inline
- **WHEN** user edits session title and confirms
- **THEN** system updates the title and reflects the change in the sidebar

### Requirement: Auto-generate session title
The system SHALL auto-generate a session title from the first user message.

#### Scenario: First message sent
- **WHEN** user sends first message in a new session
- **THEN** system sets session title to first 30 characters of the message

### Requirement: Search sessions
The system SHALL allow user to search sessions by keyword.

#### Scenario: Search with keyword
- **WHEN** user enters a search keyword
- **THEN** system returns sessions whose messages contain the keyword, with match snippet and count

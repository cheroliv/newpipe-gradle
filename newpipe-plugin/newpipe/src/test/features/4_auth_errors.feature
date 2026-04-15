#noinspection CucumberUndefinedStep
@cucumber @newpipe @auth-errors
Feature: Authentication error handling

  # ------------------------------------------------------------------
  # US-4.2 : Token révoqué par l'utilisateur
  # ------------------------------------------------------------------

  Scenario: Token révoqué par l'utilisateur
    Given a Newpipe project with a session with revoked refreshToken
    When I am executing the task 'download' in mock mode
    Then the build should succeed
    And the output should contain "Token révoqué par l'utilisateur"
    And the session should be marked as invalid
    And other valid sessions should be used

  # ------------------------------------------------------------------
  # US-4.2 : Token expiré (valable 1 an)
  # ------------------------------------------------------------------

  Scenario: Token expiré après un an
    Given a Newpipe project with a session with expired refreshToken
    When I am executing the task 'download' in mock mode
    Then the build should succeed
    And the output should contain "Token expiré (valable 1 an)"
    And the output should contain "./gradlew authSessions pour renouveler"

  # ------------------------------------------------------------------
  # US-4.2 : client_secret invalide
  # ------------------------------------------------------------------

  Scenario: client_secret invalide
    Given a Newpipe project with a session with invalid client_secret
    When I am executing the task 'download' in mock mode
    Then the build should succeed
    And the output should contain "client_secret invalide"
    And the output should contain "./gradlew buildSessions"
    And the session should be marked as critical

  # ------------------------------------------------------------------
  # US-4.2 : Multiple sessions avec erreurs partielles
  # ------------------------------------------------------------------

  Scenario: Multiple sessions avec erreurs partielles
    Given a Newpipe project with 3 sessions: 1 valid, 1 revoked, 1 expired
    When I am executing the task 'download' in mock mode
    Then the build should succeed
    And the output should report invalid sessions
    And the valid session should be used for downloads

  # ------------------------------------------------------------------
  # US-4.2 : Toutes sessions invalides
  # ------------------------------------------------------------------

  Scenario: Toutes sessions invalides - fallback anonyme
    Given a Newpipe project with 3 sessions all invalid
    When I am executing the task 'download' in mock mode
    Then the build should succeed
    And the output should contain "Aucune session valide"
    And the download should continue in anonymous mode
    And the output should contain "./gradlew authSessions"

  # ------------------------------------------------------------------
  # US-4.3 : HTTP 401 - Unauthorized
  # ------------------------------------------------------------------

  Scenario: HTTP 401 retourne par YouTube API
    Given a Newpipe project with a valid session
    And YouTube API returns HTTP 401 for this session
    When I am executing the task 'download' in mock mode
    Then the build should succeed
    And the output should contain "Token révoqué ou expiré"
    And the session should be marked as invalid
    And another session should be tried

  # ------------------------------------------------------------------
  # US-4.3 : HTTP 403 - Forbidden (quota exceeded)
  # ------------------------------------------------------------------

  Scenario: HTTP 403 - Quota API dépassé
    Given a Newpipe project with a session that exceeded API quota
    And YouTube API returns HTTP 403 for quota exceeded
    When I am executing the task 'download' in mock mode
    Then the build should succeed
    And the output should contain "quota dépassé"
    And automatic fallback to another session should occur

  # ------------------------------------------------------------------
  # US-4.3 : HTTP 403 - Forbidden (account suspended)
  # ------------------------------------------------------------------

  Scenario: HTTP 403 - Compte Google suspendu
    Given a Newpipe project with a suspended Google account session
    And YouTube API returns HTTP 403 for suspended account
    When I am executing the task 'download' in mock mode
    Then the build should succeed
    And the output should contain "compte suspendu"
    And the session should be permanently disabled

  # ------------------------------------------------------------------
  # US-4.4 : OAuth2 error - invalid_grant (revoked)
  # ------------------------------------------------------------------

  Scenario: OAuth2 error invalid_grant - token revoked
    Given a Newpipe project with a session
    And token refresh returns OAuth2 error "invalid_grant" with "revoked"
    When I am executing the task 'authSessions'
    Then the build should succeed
    And the output should contain "Token révoqué par l'utilisateur"

  # ------------------------------------------------------------------
  # US-4.4 : OAuth2 error - invalid_grant (expired)
  # ------------------------------------------------------------------

  Scenario: OAuth2 error invalid_grant - token expired
    Given a Newpipe project with a session
    And token refresh returns OAuth2 error "invalid_grant" with "expired"
    When I am executing the task 'authSessions'
    Then the build should succeed
    And the output should contain "Token expiré (valable 1 an)"

  # ------------------------------------------------------------------
  # US-4.4 : OAuth2 error - invalid_client
  # ------------------------------------------------------------------

  Scenario: OAuth2 error invalid_client
    Given a Newpipe project with a session with invalid client credentials
    And token refresh returns OAuth2 error "invalid_client"
    When I am executing the task 'authSessions'
    Then the build should succeed
    And the output should contain "client_secret invalide"
    And the output should contain "client_secrets/"

  # ------------------------------------------------------------------
  # US-4.4 : Network errors - timeout
  # ------------------------------------------------------------------

  Scenario: Network error - socket timeout
    Given a Newpipe project with a valid session
    And network request times out
    When I am executing the task 'download' in mock mode
    Then the build should succeed
    And the output should contain "Délai d'attente dépassé"
    And the session should NOT be marked as invalid

  # ------------------------------------------------------------------
  # US-4.4 : Error logging with emoji
  # ------------------------------------------------------------------

  Scenario: Warning errors logged with ⚠️ emoji
    Given a Newpipe project with a session with warning-level error
    When I am executing the task 'download' in mock mode
    Then the build should succeed
    And the output should contain "⚠️"

  Scenario: Critical errors logged with 🚫 emoji
    Given a Newpipe project with a session with critical-level error
    When I am executing the task 'download' in mock mode
    Then the build should succeed
    And the output should contain "🚫"

#noinspection CucumberUndefinedStep
@cucumber @newpipe
Feature: Authentication session management

  # ------------------------------------------------------------------
  # buildSessions — JSON → sessions.yml
  # ------------------------------------------------------------------

  Scenario: buildSessions generates sessions.yml from client_secrets/ JSON files
    Given a Newpipe project with a client_secrets directory
    When I am executing the task 'buildSessions'
    Then the build should succeed
    And sessions.yml should exist in the project
    And sessions.yml should contain the fake clientId
    And sessions.yml should contain an empty refreshToken

  # ------------------------------------------------------------------
  # authSessions — refreshToken présent → skip Device Flow
  # ------------------------------------------------------------------

  Scenario: authSessions skips Device Flow when refreshToken is already present
    Given a Newpipe project wired to the real sessions.yml
    When I am executing the task 'authSessions'
    Then the build should succeed
    And the output should contain "Refresh token present — skipping Device Flow"

  # ------------------------------------------------------------------
  # authSessions — refreshToken absent → Device Flow détecté
  # ------------------------------------------------------------------

  Scenario: authSessions detects missing refreshToken and signals Device Flow is needed
    Given a Newpipe project with a sessions.yml without refreshToken
    When I am executing the task 'authSessions'
    Then the build should succeed
    And the output should contain "No token — starting Device Flow"

  # ------------------------------------------------------------------
  # download — mode anonyme quand aucun sessionsPath configuré
  # ------------------------------------------------------------------

  Scenario: download runs in anonymous mode when no sessionsPath is configured
    Given a new Newpipe project
    When I am executing the task 'download' in mock mode
    Then the build should succeed

  # ------------------------------------------------------------------
  # download — SessionManager initialisé depuis le vrai sessions.yml
  # ------------------------------------------------------------------

  Scenario: download initialises SessionManager when refreshToken is present
    Given a Newpipe project wired to the real sessions.yml
    When I am executing the task 'download' in mock mode
    Then the build should succeed
    And the output should contain "SessionManager"

#noinspection CucumberUndefinedStep
@cucumber @newpipe
Feature: Minimal newpipe configuration

  Scenario: Canary
    Given a new Newpipe project
    When I am executing the task 'tasks'
    Then the build should succeed

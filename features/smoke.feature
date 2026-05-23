@smoke
Feature: Smoke tests - microservicios via gateway (puerto 8090)

  # ── Products ──────────────────────────────────────────────────────────────

  @smoke
  Scenario: GET /api/products returns 200
    When I make a GET request to "api/products"
    Then the HTTP status code is equal to 200

  @smoke
  Scenario: GET /api/products/1 returns 200 with product data
    When I make a GET request to "api/products/1"
    Then the HTTP status code is equal to 200
    And the response body contains:
      """json
      {"id": 1}
      """

  @smoke
  Scenario: GET /api/products/999999 returns 404
    When I make a GET request to "api/products/999999"
    Then the HTTP status code is equal to 404

  # ── Items ─────────────────────────────────────────────────────────────────

  @smoke
  Scenario: GET /api/items returns 200
    When I make a GET request to "api/items"
    Then the HTTP status code is equal to 200

  @smoke
  Scenario: GET /api/items/1 returns 200 with item and embedded product
    When I make a GET request to "api/items/1"
    Then the HTTP status code is equal to 200
    And the response body contains:
      """json
      {"quantity": 1}
      """

  @smoke
  Scenario: GET /api/items/999999 returns 404
    When I make a GET request to "api/items/999999"
    Then the HTTP status code is equal to 404

# frozen_string_literal: true

require_relative "test_helper"

class TestBetHistory < Minitest::Test
  include E2EHelpers

  def test_bet_history_shows_settled_bet
    # Setup: create wallet, place bet, wait for settlement
    create_wallet("HistoryTester", "1000")
    wait_for_ws_connection(timeout: 15)
    wait_for_prices(timeout: 15)
    place_bet("BTCUSDT", "UP", "100")

    # Wait for active bet then settlement
    wait_for("#active-bets .active-bet", timeout: 10)
    wait_for_settling_or_settled(timeout: 25)
    wait_for_text("#active-bets", "No active bets", timeout: 20)

    # Navigate to history view
    @browser.evaluate("document.querySelector('.sidebar__link[data-view=\"history\"]').click()")
    sleep 0.5

    # Wait for history table to render
    wait_for(".history-table", timeout: 5)

    # Verify at least 1 row exists
    row_count = @browser.evaluate("document.querySelectorAll('.history-table tbody tr').length")
    assert row_count >= 1, "Expected at least 1 history row, got #{row_count}"

    # Verify row contains bet symbol and direction
    row_text = @browser.evaluate("document.querySelector('.history-table tbody tr').textContent")
    assert_includes row_text, "BTCUSDT", "History row should contain bet symbol"
    assert_includes row_text, "UP", "History row should contain bet direction"

    # Verify badge with status
    badge = wait_for(".history-table .badge", timeout: 5)
    badge_text = badge.text
    assert_match(/WON|LOST|PUSH/, badge_text, "Badge should show WON, LOST, or PUSH")

    # Click Won filter tab and wait briefly
    @browser.evaluate("document.querySelector('.history-tab[data-filter=\"WON\"]').click()")
    sleep 1
    # Filter applied — table may or may not have rows depending on outcome (non-deterministic)
  end

  private

  def wait_for_settling_or_settled(timeout: 25)
    deadline = Time.now + timeout
    loop do
      active_text = @browser.evaluate("document.querySelector('#active-bets').textContent")
      if active_text.include?("Settling") || active_text.include?("No active bets")
        return true
      end
      raise "Timeout waiting for bet to settle" if Time.now > deadline
      sleep 0.5
    end
  end
end

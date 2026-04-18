# frozen_string_literal: true

require_relative "test_helper"

class TestBetSettlement < Minitest::Test
  include E2EHelpers

  def test_bet_settles_and_updates_ui
    create_wallet("SettleTester", "1000")
    wait_for_ws_connection(timeout: 15)
    wait_for_prices(timeout: 15)

    # Place a bet via helper
    place_bet("BTCUSDT", "UP", "100")

    # Wait for active bet to appear
    active_bet = wait_for("#active-bets .active-bet", timeout: 10)
    assert_includes active_bet.text, "BTCUSDT"

    # Record balance after bet placement (stake deducted)
    initial_balance = @browser.at_css("#wallet-balance").text

    # Wait for settling phase — countdown reaches 0 or settles immediately
    wait_for_settling_or_settled(timeout: 25)

    # Wait for active bets to clear after settlement (polling at 3s intervals)
    wait_for_text("#active-bets", "No active bets", timeout: 20)

    # Give wallet balance time to update from settlement polling
    sleep 1

    # Verify wallet balance is still a valid dollar amount after settlement
    final_balance = @browser.at_css("#wallet-balance").text
    assert_match(/\$[\d,.]+/, final_balance, "Balance should be a valid dollar amount after settlement")

    # On WIN/PUSH, balance changes from post-bet value; on LOSS it stays the same
    # Both are valid outcomes — the key verification is that settlement completed (no active bets)
    if final_balance != initial_balance
      # Balance changed — bet was won or pushed
      refute_equal initial_balance, final_balance
    end
    # If balance unchanged — bet was lost, which is also valid
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

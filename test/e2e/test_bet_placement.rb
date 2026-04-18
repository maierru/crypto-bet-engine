# frozen_string_literal: true

require_relative "test_helper"

class TestBetPlacement < Minitest::Test
  include E2EHelpers

  def test_bet_placement_full_flow
    create_wallet("BetTester", "1000")
    wait_for_ws_connection(timeout: 15)
    wait_for_prices(timeout: 15)

    # Verify submit button is disabled initially
    submit_disabled = @browser.evaluate("document.querySelector('#bet-submit-btn').disabled")
    assert submit_disabled, "Submit button should be disabled initially"

    # Select BTCUSDT from symbol dropdown
    select_option("#bet-symbol", "BTCUSDT")

    # Click UP direction button
    @browser.at_css(".bet-dir-btn[data-dir='UP']").click

    # Wait for odds to load (API call)
    wait_for_text_match("#bet-odds", /\d+\.\d+/, timeout: 10)

    # Verify bet-info is visible
    info_display = @browser.evaluate("document.querySelector('#bet-info').style.display")
    refute_equal "none", info_display, "#bet-info should be visible"

    # Enter bet amount
    fill_input("#bet-amount", "100")

    # Wait for payout to calculate
    wait_for_text_match("#bet-payout", /\$\d+/, timeout: 5)

    # Verify submit button is enabled
    sleep 0.3
    submit_disabled = @browser.evaluate("document.querySelector('#bet-submit-btn').disabled")
    refute submit_disabled, "Submit button should be enabled after filling form"

    # Click submit (use JS click — Ferrum native click unreliable when button may be below fold)
    @browser.evaluate("document.querySelector('#bet-submit-btn').click()")

    # Verify confirm modal appears
    wait_for_visible("#bet-confirm-modal", timeout: 5)

    # Verify confirm details contain expected values
    details_text = @browser.at_css("#bet-confirm-details").text
    assert_includes details_text, "BTCUSDT", "Confirm should show symbol"
    assert_includes details_text, "UP", "Confirm should show direction"
    assert_includes details_text, "$100.00", "Confirm should show stake"

    # Click confirm (use JS click for consistency)
    @browser.evaluate("document.querySelector('#bet-confirm-btn').click()")

    # Verify confirm modal hides
    wait_for_hidden("#bet-confirm-modal", timeout: 10)

    # Verify success toast
    wait_for("#toast-container .toast--success", timeout: 10)

    # Verify active bet appears
    active_bet = wait_for("#active-bets .active-bet", timeout: 10)
    assert_includes active_bet.text, "BTCUSDT", "Active bet should show BTCUSDT"
  end
end

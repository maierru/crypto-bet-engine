# frozen_string_literal: true

require_relative "test_helper"

class DepositTest < Minitest::Test
  include E2EHelpers

  def test_deposit_flow
    create_wallet("DepositBot", "1000")

    # Wait for wallet card to render with deposit button
    wait_for("#deposit-toggle-btn", timeout: 10)

    # Click deposit toggle
    @browser.at_css("#deposit-toggle-btn").click

    # Verify deposit section becomes visible
    wait_for_visible("#deposit-section")

    # Enter deposit amount
    fill_input("#deposit-amount", "500")

    # Submit deposit form
    @browser.at_css("#deposit-form button[type='submit']").click

    # Verify success toast
    wait_for(".toast--success", timeout: 5)

    # Verify updated balance
    wait_for_text("#wallet-balance", "$1500.00", timeout: 5)
  end
end

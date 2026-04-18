# frozen_string_literal: true

require_relative "test_helper"

class WalletCreationTest < Minitest::Test
  include E2EHelpers

  def test_wallet_creation_happy_path
    # Verify wallet modal is visible on fresh page
    wait_for_visible("#wallet-modal")

    # Fill in wallet form
    fill_input("#wallet-nickname", "TestBot")
    fill_input("#wallet-initial-balance", "1000")

    # Submit
    @browser.at_css("#create-wallet-form button[type='submit']").click

    # Verify modal hides
    wait_for_hidden("#wallet-modal")

    # Verify header wallet indicator
    wait_for_text("#wallet-name", "TestBot")
    wait_for_text("#wallet-balance", "$1000.00")

    # Verify wallet details section contains nickname
    wait_for_text("#wallet-details", "TestBot")
  end

  def test_validation_short_nickname
    wait_for_visible("#wallet-modal")

    # Use JS to bypass HTML5 minlength so we can test JS validation
    @browser.evaluate("document.getElementById('wallet-nickname').removeAttribute('minlength')")
    @browser.evaluate("document.getElementById('wallet-nickname').removeAttribute('required')")

    fill_input("#wallet-nickname", "A")
    fill_input("#wallet-initial-balance", "1000")
    @browser.at_css("#create-wallet-form button[type='submit']").click

    # JS validation should show error
    wait_for_text("#wallet-form-error", "Nickname must be at least 2 characters")

    # Modal should still be visible
    el = @browser.at_css("#wallet-modal")
    display = @browser.evaluate("document.querySelector('#wallet-modal').style.display")
    refute_equal "none", display
  end

  def test_validation_zero_balance
    wait_for_visible("#wallet-modal")

    # Remove HTML5 min attribute to test JS validation
    @browser.evaluate("document.getElementById('wallet-initial-balance').removeAttribute('min')")

    fill_input("#wallet-nickname", "TestBot")
    fill_input("#wallet-initial-balance", "0")
    @browser.at_css("#create-wallet-form button[type='submit']").click

    # JS validation should show error
    wait_for_text("#wallet-form-error", "Balance must be greater than 0")

    # Modal should still be visible
    el = @browser.at_css("#wallet-modal")
    display = @browser.evaluate("document.querySelector('#wallet-modal').style.display")
    refute_equal "none", display
  end
end

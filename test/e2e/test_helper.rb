# frozen_string_literal: true

require "bundler/setup"
require "minitest/autorun"
require "ferrum"
require "fileutils"

BASE_URL = ENV.fetch("APP_URL", "http://localhost:8080")
HEADLESS = ENV.fetch("HEADLESS", "true") != "false"

module E2EHelpers
  def setup
    browser_opts = { headless: HEADLESS, timeout: 30, window_size: [1280, 800] }

    if RUBY_PLATFORM.include?("linux")
      browser_opts[:browser_options] = { "no-sandbox" => nil }
    end

    @browser = Ferrum::Browser.new(**browser_opts)
    @browser.goto(BASE_URL)
  end

  def teardown
    if !passed? && @browser
      FileUtils.mkdir_p(File.join(__dir__, "screenshots"))
      path = File.join(__dir__, "screenshots", "#{name}_#{Time.now.strftime('%Y%m%d_%H%M%S')}.png")
      @browser.screenshot(path: path)
    end
    @browser&.quit
  end

  # --- Wait helpers ---

  def wait_for(selector, timeout: 10)
    deadline = Time.now + timeout
    loop do
      el = @browser.at_css(selector)
      return el if el
      raise "Timeout waiting for #{selector}" if Time.now > deadline
      sleep 0.2
    end
  end

  def wait_for_visible(selector, timeout: 10)
    deadline = Time.now + timeout
    loop do
      el = @browser.at_css(selector)
      if el
        display = @browser.evaluate("document.querySelector('#{selector}').style.display")
        return el if display != "none"
      end
      raise "Timeout waiting for #{selector} to be visible" if Time.now > deadline
      sleep 0.2
    end
  end

  def wait_for_hidden(selector, timeout: 10)
    deadline = Time.now + timeout
    loop do
      el = @browser.at_css(selector)
      if el.nil?
        return true
      else
        display = @browser.evaluate("document.querySelector('#{selector}').style.display")
        return true if display == "none"
      end
      raise "Timeout waiting for #{selector} to be hidden" if Time.now > deadline
      sleep 0.2
    end
  end

  def wait_for_text(selector, text, timeout: 10)
    deadline = Time.now + timeout
    loop do
      el = @browser.at_css(selector)
      if el
        content = el.text
        return el if content.include?(text)
      end
      raise "Timeout waiting for '#{text}' in #{selector}" if Time.now > deadline
      sleep 0.2
    end
  end

  def wait_for_text_match(selector, pattern, timeout: 10)
    deadline = Time.now + timeout
    loop do
      el = @browser.at_css(selector)
      if el
        content = el.text
        return el if content.match?(pattern)
      end
      raise "Timeout waiting for pattern #{pattern.inspect} in #{selector}" if Time.now > deadline
      sleep 0.2
    end
  end

  def wait_for_class(selector, css_class, timeout: 10)
    deadline = Time.now + timeout
    loop do
      el = @browser.at_css(selector)
      if el
        classes = @browser.evaluate("document.querySelector('#{selector}').className")
        return el if classes.include?(css_class)
      end
      raise "Timeout waiting for class '#{css_class}' on #{selector}" if Time.now > deadline
      sleep 0.2
    end
  end

  # --- Action helpers ---

  def create_wallet(nickname = "TestBot", balance = "1000")
    wait_for_visible("#wallet-modal")
    fill_input("#wallet-nickname", nickname)
    fill_input("#wallet-initial-balance", balance)
    @browser.at_css("#create-wallet-form button[type='submit']").click
    wait_for_hidden("#wallet-modal")
  end

  def wait_for_ws_connection(timeout: 15)
    wait_for_class("#conn-status", "conn-status--connected", timeout: timeout)
  end

  def wait_for_prices(timeout: 15)
    wait_for("#price-card-BTCUSDT", timeout: timeout)
  end

  def place_bet(symbol = "BTCUSDT", direction = "UP", amount = "100")
    select_option("#bet-symbol", symbol)
    @browser.at_css(".bet-dir-btn[data-dir='#{direction}']").click
    fill_input("#bet-amount", amount)
    wait_for("#bet-submit-btn:not([disabled])", timeout: 5)
    @browser.at_css("#bet-submit-btn").click
    wait_for_visible("#bet-confirm-modal")
    @browser.at_css("#bet-confirm-btn").click
    wait_for_hidden("#bet-confirm-modal")
  end

  private

  def fill_input(selector, value)
    el = wait_for(selector)
    el.focus
    el.evaluate("this.value = ''")
    el.type(value)
  end

  def select_option(selector, value)
    @browser.evaluate("document.querySelector('#{selector}').value = '#{value}'")
    @browser.evaluate("document.querySelector('#{selector}').dispatchEvent(new Event('change'))")
  end
end

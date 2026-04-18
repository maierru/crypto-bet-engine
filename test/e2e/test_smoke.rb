# frozen_string_literal: true

require_relative "test_helper"

class SmokeTest < Minitest::Test
  include E2EHelpers

  def test_page_loads_with_correct_title
    el = wait_for("h1.header__title")
    assert_equal "Crypto Bet Engine", el.text
  end
end

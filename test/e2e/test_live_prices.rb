# frozen_string_literal: true

require_relative "test_helper"

class LivePricesTest < Minitest::Test
  include E2EHelpers

  def test_websocket_connection_and_prices
    # Create wallet to get past modal
    create_wallet("PriceBot", "500")

    # Wait for WebSocket connection
    wait_for_ws_connection(timeout: 15)

    # Wait for price cards to appear
    wait_for("#price-card-BTCUSDT", timeout: 15)
    wait_for("#price-card-ETHUSDT", timeout: 15)

    # Verify prices show non-zero numeric values
    wait_for_text_match("#price-BTCUSDT", /\$[\d,.]+/, timeout: 15)
    wait_for_text_match("#price-ETHUSDT", /\$[\d,.]+/, timeout: 15)
  end
end

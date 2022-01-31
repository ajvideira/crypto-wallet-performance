package br.com.ajvideira.genenis_cg;

import java.math.BigDecimal;

public class CryptoAsset {

	public String id;
	public String symbol;
	public BigDecimal quantity;
	public BigDecimal price;
	
	public boolean isProcessing = false;
	public boolean wasProcessed = false;
	
	public BigDecimal actualPrice;
	public BigDecimal performance;
	
	public CryptoAsset(String symbol, BigDecimal quantity, BigDecimal price) {
		this.symbol = symbol;
		this.quantity = quantity;
		this.price = price;
	}

	@Override
	public String toString() {
		return "CryptoAsset [id=" + id + ", symbol=" + symbol + ", quantity=" + quantity + ", price=" + price
				+ ", isProcessing=" + isProcessing + ", wasProcessed=" + wasProcessed + ", actualPrice=" + actualPrice
				+ ", performance=" + performance + "]";
	}

}

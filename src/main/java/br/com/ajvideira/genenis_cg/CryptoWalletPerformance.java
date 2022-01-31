package br.com.ajvideira.genenis_cg;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONObject;

public class CryptoWalletPerformance {
	private final String DELIMITER = ",";
	private final String API_URL = "https://api.coincap.io/v2";
	private final String API_KEY = "YOUR_API_KEY";

	public Map<String, CryptoAsset> assets = null;

	public CryptoWalletPerformance() {

	}

	public void readCSVFile() throws Exception {

		this.assets = new HashMap<String, CryptoAsset>();

		try (BufferedReader br = new BufferedReader(new FileReader("src/main/resources/wallet.csv"))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] values = line.split(DELIMITER);
				assets.put(values[0], new CryptoAsset(values[0], new BigDecimal(values[1]), new BigDecimal(values[2])));
			}
		}
	}

	public synchronized CryptoAsset getCryptoToUpdate() {
		Entry<String, CryptoAsset> result = this.assets.entrySet().stream()
				.filter(entry -> !entry.getValue().isProcessing && !entry.getValue().wasProcessed).findFirst()
				.orElseGet(null);

		if (result == null) {
			return null;
		} else {
			CryptoAsset crypto = result.getValue();
			crypto.isProcessing = true;
			this.assets.put(crypto.symbol, crypto);
			return crypto;
		}
	}

	public synchronized void updateCryptoAsset(CryptoAsset asset) {
		asset.isProcessing = false;
		asset.wasProcessed = true;
		this.assets.put(asset.symbol, asset);
	}

	public String callAPI(String path) throws Exception {
		URL url = new URL(API_URL + path);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("GET");
		connection.setRequestProperty("Authorization", "Bearer " + API_KEY);

		int statusCode = connection.getResponseCode();

		if (statusCode != 200) {
			throw new Exception("Error calling API");
		}

		BufferedReader response = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		String inputLine;
		StringBuffer content = new StringBuffer();
		while ((inputLine = response.readLine()) != null) {
			content.append(inputLine);
		}

		response.close();
		connection.disconnect();

		return content.toString();
	}

	@SuppressWarnings("unchecked")
	public void findCryptosIDs() throws Exception {
		JSONObject response = new JSONObject(callAPI("/assets"));
		JSONArray data = response.getJSONArray("data");

		List<Object> cryptoList = data.toList();

		assets.keySet().stream().forEach(key -> {
			for (Object cryptoJson : cryptoList) {
				String cryptoSymbol = ((Map<String, Object>) cryptoJson).get("symbol").toString();
				if (cryptoSymbol.equalsIgnoreCase(key)) {
					assets.get(key).id = ((Map<String, Object>) cryptoJson).get("id").toString();
					break;
				}
			}
		});
	}

	public void checkCryptoHistory(CryptoAsset asset) throws Exception {
		JSONObject response = new JSONObject(
				callAPI("/assets/" + asset.id + "/history?interval=d1&start=1617753600000&end=1617753601000"));
		JSONObject data = response.getJSONArray("data").getJSONObject(0);
		asset.actualPrice = data.getBigDecimal("priceUsd");
		asset.performance = asset.actualPrice.divide(asset.price, 2, RoundingMode.HALF_UP);

		updateCryptoAsset(asset);
	}

	public void summarizeData() {
		BigDecimal total = this.assets.entrySet().stream()
				.map(entry -> entry.getValue().actualPrice.multiply(entry.getValue().quantity))
				.reduce(BigDecimal.ZERO, (x, y) -> x.add(y));

		CryptoAsset identity = this.assets.entrySet().stream().findAny().get().getValue();

		CryptoAsset bestAsset = this.assets.entrySet().stream().map(entry -> entry.getValue()).reduce(identity,
				(x, y) -> x.performance.compareTo(y.performance) >= 0 ? x : y);

		CryptoAsset worstAsset = this.assets.entrySet().stream().map(entry -> entry.getValue()).reduce(identity,
				(x, y) -> x.performance.compareTo(y.performance) <= 0 ? x : y);
		System.out.println(
				"total=" + total + ",best_asset=" + bestAsset.symbol + ",best_performance=" + bestAsset.performance
						+ ",worst_asset=" + worstAsset.symbol + ",worst_performance=" + worstAsset.performance);
	}

	public static void main(String[] args) {
		CryptoWalletPerformance cryptoWallet = new CryptoWalletPerformance();

		try {
			cryptoWallet.readCSVFile();

			cryptoWallet.findCryptosIDs();

			int assetsCount = cryptoWallet.assets.keySet().size();
			int threadsCount = assetsCount > 2 ? 3 : assetsCount;

			ExecutorService WORKER_THREAD_POOL = Executors.newFixedThreadPool(threadsCount);
			CountDownLatch latch = new CountDownLatch(assetsCount);
			for (int i = 0; i < threadsCount; i++) {

				WORKER_THREAD_POOL.submit(() -> {
					while (true) {
						CryptoAsset crypto = cryptoWallet.getCryptoToUpdate();

						if (crypto == null) {
							Thread.currentThread().interrupt();
						} else {
							cryptoWallet.checkCryptoHistory(crypto);
							latch.countDown();
						}
					}
				});
			}

			latch.await();

			cryptoWallet.assets.entrySet().forEach(entry -> System.out.println(entry.getValue()));

			cryptoWallet.summarizeData();

		} catch (Exception e) {
			e.printStackTrace();
		}

		System.exit(0);

	}
}

package api;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private final TimeUnit timeUnit;
    private final int requestLimit;
    private final HttpClient httpClient;
    private final ReentrantLock lock;
    private final Condition condition;
    private int requestCount;
    private long startTime;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.httpClient = HttpClient.newHttpClient();
        this.lock = new ReentrantLock();
        this.condition = lock.newCondition();
        this.requestCount = 0;
        this.startTime = System.currentTimeMillis();
    }

    public void createDocument(Document document, String signature) throws InterruptedException, IOException {
        lock.lock();
        try {
            long interval = timeUnit.toMillis(1);
            long currentTime = System.currentTimeMillis();

            while (requestCount >= requestLimit) {
                long remainingTime = interval - (currentTime - startTime);
                if (remainingTime > 0) {
                    condition.await(remainingTime, TimeUnit.MILLISECONDS);
                }
                currentTime = System.currentTimeMillis();
                if (currentTime - startTime >= interval) {
                    requestCount = 0;
                    startTime = currentTime;
                }
            }
            requestCount++;
        } finally {
            lock.unlock();
        }

        JSONObject json = new JSONObject();
        json.put("description", document.toJsonObject());
        json.put("signature", signature);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to create document: " + response.body());
        }

        System.out.println("Document created successfully: " + response.body());
    }

    public static class Document {
        private String participantInn;
        private String docId;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        private String producerInn;
        private String productionDate;
        private String productionType;
        private List<Product> products;
        private String regDate;
        private String regNumber;

        public Document(String participantInn, String docId, String docStatus, String docType, boolean importRequest,
                        String ownerInn, String producerInn, String productionDate, String productionType,
                        List<Product> products, String regDate, String regNumber) {
            this.participantInn = participantInn;
            this.docId = docId;
            this.docStatus = docStatus;
            this.docType = docType;
            this.importRequest = importRequest;
            this.ownerInn = ownerInn;
            this.producerInn = producerInn;
            this.productionDate = productionDate;
            this.productionType = productionType;
            this.products = products;
            this.regDate = regDate;
            this.regNumber = regNumber;
        }

        public JSONObject toJsonObject() {
            JSONObject json = new JSONObject();
            json.put("participantInn", participantInn);
            json.put("doc_id", docId);
            json.put("doc_status", docStatus);
            json.put("doc_type", docType);
            json.put("importRequest", importRequest);
            json.put("owner_inn", ownerInn);
            json.put("producer_inn", producerInn);
            json.put("production_date", productionDate);
            json.put("production_type", productionType);

            JSONArray productsArray = new JSONArray();
            for (Product product : products) {
                productsArray.put(product.toJsonObject());
            }
            json.put("products", productsArray);

            json.put("reg_date", regDate);
            json.put("reg_number", regNumber);

            return json;
        }

    }

    public static class Product {
        private String certificateDocument;
        private String certificateDocumentDate;
        private String certificateDocumentNumber;
        private String ownerInn;
        private String producerInn;
        private String productionDate;
        private String tnvedCode;
        private String uitCode;
        private String uituCode;

        public Product(String certificateDocument, String certificateDocumentDate, String certificateDocumentNumber,
                       String ownerInn, String producerInn, String productionDate, String tnvedCode, String uitCode,
                       String uituCode) {
            this.certificateDocument = certificateDocument;
            this.certificateDocumentDate = certificateDocumentDate;
            this.certificateDocumentNumber = certificateDocumentNumber;
            this.ownerInn = ownerInn;
            this.producerInn = producerInn;
            this.productionDate = productionDate;
            this.tnvedCode = tnvedCode;
            this.uitCode = uitCode;
            this.uituCode = uituCode;
        }

        public JSONObject toJsonObject() {
            JSONObject json = new JSONObject();
            json.put("certificate_document", certificateDocument);
            json.put("certificate_document_date", certificateDocumentDate);
            json.put("certificate_document_number", certificateDocumentNumber);
            json.put("owner_inn", ownerInn);
            json.put("producer_inn", producerInn);
            json.put("production_date", productionDate);
            json.put("tnved_code", tnvedCode);
            json.put("uit_code", uitCode);
            json.put("uitu_code", uituCode);
            return json;
        }

    }

    public static void main(String[] args) {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 3);

        Product product = new Product("certDoc", "2023-07-01", "certNum", "1234567890",
                "0987654321", "2023-07-01", "1234", "5678", "91011");
        List<Product> products = List.of(product);

        Document document = new Document("1234567890", "doc123", "NEW", "LP_INTRODUCE_GOODS", true,
                "1234567890", "0987654321", "2023-07-01", "TYPE", products, "2023-07-01", "reg123");

        try {
            api.createDocument(document, "Tikhon Vergentev");
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }
}

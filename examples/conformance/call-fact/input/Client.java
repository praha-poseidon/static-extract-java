package demo;

class HttpClient {
    String get(String path) {
        return path;
    }
}

class Client {
    void load() {
        new HttpClient().get("/api/items");
    }
}


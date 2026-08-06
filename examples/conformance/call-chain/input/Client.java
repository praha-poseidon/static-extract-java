package demo;

class WebClient {
  WebClient post() { return this; }
  WebClient uri(String path) { return this; }
  void retrieve() {}
}

class Client {
  void call(WebClient client) {
    client.post().uri("/api/users").retrieve();
  }
}

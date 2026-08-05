package demo;

@interface ConfigProperty {
    String value();
}

class AppConfig {
    @ConfigProperty("service.url")
    String serviceUrl = "http://localhost";
}


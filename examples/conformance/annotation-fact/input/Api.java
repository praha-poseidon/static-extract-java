package demo;

@interface RouteGet {
    String value();
}

class Api {
    @RouteGet("/api/users")
    String users() {
        return "ok";
    }
}


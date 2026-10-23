package dev.olsa777.clouddesk.request;

public class RequestNotFoundException extends RuntimeException {
    public RequestNotFoundException(long id) {
        super("Заявка #" + id + " не найдена");
    }
}

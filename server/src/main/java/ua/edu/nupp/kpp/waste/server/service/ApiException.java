package ua.edu.nupp.kpp.waste.server.service;

public final class ApiException extends RuntimeException {
    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() { return status; }

    public static ApiException badRequest(String message) { return new ApiException(400, message); }
    public static ApiException notFound() { return new ApiException(404, "Запис не знайдено"); }
    public static ApiException conflict(String message) { return new ApiException(409, message); }
}

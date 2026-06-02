package org.springframework.samples.petclinic.error;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Standard error response object that will be returned to the client
 * in case of security exceptions.
 */
public class ErrorResponse {

    private final int code;
    private final String message;

    @JsonProperty("server_address")
    private final String serverAddress;

    public ErrorResponse(int code, String message, String serverAddress) {
        this.code = code;
        this.message = message;
        this.serverAddress = serverAddress;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getServerAddress() {
        return serverAddress;
    }
}

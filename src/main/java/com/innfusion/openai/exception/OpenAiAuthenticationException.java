package com.innfusion.openai.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innfusion.utils.Utils;

public class OpenAiAuthenticationException extends RuntimeException {

    public OpenAiAuthenticationException(String message) {
        super(Utils.extractMessage(message));
    }

    public OpenAiAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }

}

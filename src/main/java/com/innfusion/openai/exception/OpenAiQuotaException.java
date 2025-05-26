package com.innfusion.openai.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innfusion.utils.Utils;

public class OpenAiQuotaException extends RuntimeException {

    public OpenAiQuotaException(String message) {
        super(Utils.extractMessage(message));
    }

    public OpenAiQuotaException(String message, Throwable cause) {
        super(message, cause);
    }

}

package com.innfusion.openai.exception.advice;

import com.innfusion.openai.exception.OpenAiAuthenticationException;
import com.innfusion.openai.exception.OpenAiQuotaException;
import org.apache.coyote.Response;
import org.springframework.boot.autoconfigure.graphql.GraphQlProperties.Http;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class OpenAiQuotaAdvice {

    @ExceptionHandler(OpenAiQuotaException.class)
    public ResponseEntity<ProblemDetail> handleOpenAiQuotaException(OpenAiQuotaException e){
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
        problemDetail.setTitle(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
        problemDetail.setDetail(e.getLocalizedMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(problemDetail);
    }

    @ExceptionHandler(OpenAiAuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleOpenAiAuthenticationException(OpenAiAuthenticationException e){
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problemDetail.setTitle(HttpStatus.UNAUTHORIZED.getReasonPhrase());
        problemDetail.setDetail(e.getLocalizedMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problemDetail);
    }

}

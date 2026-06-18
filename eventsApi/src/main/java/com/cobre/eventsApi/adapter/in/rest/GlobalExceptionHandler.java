package com.cobre.eventsApi.adapter.in.rest;

import com.cobre.eventsApi.adapter.in.rest.dto.ErrorResponse;
import com.cobre.eventsApi.domain.exception.EventAlreadyCompletedException;
import com.cobre.eventsApi.domain.exception.EventAlreadyInProgressException;
import com.cobre.eventsApi.domain.exception.EventNotFoundException;
import com.cobre.eventsApi.domain.exception.InvalidTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.format.DateTimeParseException;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidToken(InvalidTokenException ex) {
        return new ErrorResponse("UNAUTHORIZED", ex.getMessage(), UUID.randomUUID().toString());
    }

    @ExceptionHandler(EventNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(EventNotFoundException ex) {
        return new ErrorResponse("EVENT_NOT_FOUND", ex.getMessage(), UUID.randomUUID().toString());
    }

    @ExceptionHandler(EventAlreadyCompletedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleAlreadyCompleted(EventAlreadyCompletedException ex) {
        return new ErrorResponse("EVENT_ALREADY_COMPLETED", ex.getMessage(), UUID.randomUUID().toString());
    }

    @ExceptionHandler(EventAlreadyInProgressException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleAlreadyInProgress(EventAlreadyInProgressException ex) {
        return new ErrorResponse("EVENT_ALREADY_IN_PROGRESS", ex.getMessage(), UUID.randomUUID().toString());
    }

    @ExceptionHandler(DateTimeParseException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadDate(DateTimeParseException ex) {
        return new ErrorResponse("INVALID_DATE_FORMAT", ex.getMessage(), UUID.randomUUID().toString());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneric(Exception ex) {
        return new ErrorResponse("INTERNAL_ERROR", ex.getMessage(), UUID.randomUUID().toString());
    }
}

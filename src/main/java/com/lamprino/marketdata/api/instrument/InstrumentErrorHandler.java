package com.lamprino.marketdata.api.instrument;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class InstrumentErrorHandler {

    @ExceptionHandler(FinancialInstrumentCatalogController.InstrumentNotFoundException.class)
    ResponseEntity<ErrorResponse> instrumentNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(new ErrorBody(
                        "instrument_not_found",
                        "Financial Instrument not found",
                        Map.of())));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            FinancialInstrumentCatalogController.InvalidLookupRequestException.class
    })
    ResponseEntity<ErrorResponse> invalidRequest() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(new ErrorBody(
                        "invalid_request",
                        "Invalid request",
                        Map.of())));
    }

    record ErrorResponse(ErrorBody error) {
    }

    record ErrorBody(String code, String message, Map<String, Object> details) {
    }
}

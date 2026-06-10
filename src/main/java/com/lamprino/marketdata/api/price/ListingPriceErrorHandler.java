package com.lamprino.marketdata.api.price;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.lamprino.marketdata.application.price.ListingPriceService;

@RestControllerAdvice
class ListingPriceErrorHandler {

    @ExceptionHandler(ListingPriceController.ListingNotFoundException.class)
    ResponseEntity<ErrorResponse> listingNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(new ErrorBody(
                        "listing_not_found",
                        "Listing not found",
                        Map.of())));
    }

    @ExceptionHandler(ListingPriceService.InvalidLatestPriceRequestException.class)
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

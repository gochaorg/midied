package xyz.cofe.mitrenier.api.server.rest;

import xyz.cofe.nipal.Status;
import xyz.cofe.nipal.StatusCode;

@Status(StatusCode.Bad_Request)
public record ClientError(String message) {}

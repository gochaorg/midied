package xyz.cofe.mitrenier.api.server.rest;

import xyz.cofe.nipal.Status;
import xyz.cofe.nipal.StatusCode;

@Status(StatusCode.Service_Unavailable)
public record ServerError(String message) {}

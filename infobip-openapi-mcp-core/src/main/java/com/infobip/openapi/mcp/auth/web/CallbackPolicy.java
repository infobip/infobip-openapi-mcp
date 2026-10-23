package com.infobip.openapi.mcp.auth.web;

import java.net.URI;
import java.util.function.Predicate;

public interface CallbackPolicy extends Predicate<URI> {}
